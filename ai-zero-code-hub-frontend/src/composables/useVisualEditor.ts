import { ref, onUnmounted, onMounted } from 'vue'

export interface SelectedElementInfo {
  tagName: string
  text: string
  html: string
  xpath: string
}

export function useVisualEditor() {
  const isEditMode = ref(false)
  const selectedElement = ref<SelectedElementInfo | null>(null)
  
  // Script to be injected into the iframe
  // We use a string here to inject it into the iframe's context
  const iframeScript = `
    (function() {
      if (window.__visualEditorInited) return;
      window.__visualEditorInited = true;

      let hoveredEl = null;
      let selectedEl = null;
      let isActive = false;

      function getXPath(element) {
        if (element.id !== '')
            return 'id("' + element.id + '")';
        if (element === document.body)
            return element.tagName;

        var ix = 0;
        var siblings = element.parentNode.childNodes;
        for (var i = 0; i < siblings.length; i++) {
            var sibling = siblings[i];
            if (sibling === element)
                return getXPath(element.parentNode) + '/' + element.tagName + '[' + (ix + 1) + ']';
            if (sibling.nodeType === 1 && sibling.tagName === element.tagName)
                ix++;
        }
      }

      function addStyle() {
        if (document.getElementById('visual-editor-style')) return;
        const style = document.createElement('style');
        style.id = 'visual-editor-style';
        style.textContent = \`
          .ve-hovered { outline: 2px dashed #1890ff !important; cursor: pointer !important; }
          .ve-selected { outline: 2px solid #1890ff !important; box-shadow: 0 0 10px rgba(24, 144, 255, 0.5) !important; }
        \`;
        document.head.appendChild(style);
      }

      function removeStyle() {
        const style = document.getElementById('visual-editor-style');
        if (style) style.remove();
        if (hoveredEl) {
            hoveredEl.classList.remove('ve-hovered');
            hoveredEl = null;
        }
        if (selectedEl) {
            selectedEl.classList.remove('ve-selected');
            selectedEl = null;
        }
      }

      function handleMouseOver(e) {
        if (!isActive) return;
        e.stopPropagation();
        if (hoveredEl && hoveredEl !== e.target) {
            hoveredEl.classList.remove('ve-hovered');
        }
        hoveredEl = e.target;
        // Don't highlight if it's the selected element
        if (hoveredEl !== selectedEl) {
            hoveredEl.classList.add('ve-hovered');
        }
      }

      function handleMouseOut(e) {
        if (!isActive) return;
        e.stopPropagation();
        if (hoveredEl && hoveredEl === e.target) {
            hoveredEl.classList.remove('ve-hovered');
            hoveredEl = null;
        }
      }

      function handleClick(e) {
        if (!isActive) return;
        e.preventDefault();
        e.stopPropagation();
        
        if (selectedEl) {
            selectedEl.classList.remove('ve-selected');
        }
        selectedEl = e.target;
        selectedEl.classList.add('ve-selected');
        selectedEl.classList.remove('ve-hovered');

        const info = {
            tagName: selectedEl.tagName.toLowerCase(),
            text: selectedEl.innerText ? selectedEl.innerText.substring(0, 200) : '',
            html: selectedEl.outerHTML.substring(0, 300),
            xpath: getXPath(selectedEl)
        };

        window.parent.postMessage({ type: 'VE_SELECTED', payload: info }, '*');
      }

      window.addEventListener('message', (event) => {
        const data = event.data;
        if (data.type === 'VE_START') {
            isActive = true;
            addStyle();
            document.body.addEventListener('mouseover', handleMouseOver, true);
            document.body.addEventListener('mouseout', handleMouseOut, true);
            document.body.addEventListener('click', handleClick, true);
        } else if (data.type === 'VE_STOP') {
            isActive = false;
            removeStyle();
            document.body.removeEventListener('mouseover', handleMouseOver, true);
            document.body.removeEventListener('mouseout', handleMouseOut, true);
            document.body.removeEventListener('click', handleClick, true);
        } else if (data.type === 'VE_CLEAR_SELECTION') {
            if (selectedEl) {
                selectedEl.classList.remove('ve-selected');
                selectedEl = null;
            }
        }
      });
    })();
  `;

  const initVisualEditor = (iframe: HTMLIFrameElement) => {
    try {
      if (!iframe.contentDocument || !iframe.contentDocument.body) return;
      // Check if script already injected
      // Since we can't easily check internal state, we just append. 
      // The script itself has a guard check.
      const doc = iframe.contentDocument;
      const script = doc.createElement('script');
      script.textContent = iframeScript;
      doc.body.appendChild(script);
    } catch (e) {
      console.warn('Cannot inject script into iframe (Cross-Origin?):', e);
    }
  }

  const toggleEditMode = (iframe: HTMLIFrameElement | undefined) => {
    if (!iframe) return;
    
    isEditMode.value = !isEditMode.value;
    
    if (isEditMode.value) {
        // Ensure script is there
        initVisualEditor(iframe);
        iframe.contentWindow?.postMessage({ type: 'VE_START' }, '*');
    } else {
        iframe.contentWindow?.postMessage({ type: 'VE_STOP' }, '*');
        selectedElement.value = null;
    }
  }

  const handleMessage = (event: MessageEvent) => {
    if (event.data?.type === 'VE_SELECTED') {
        selectedElement.value = event.data.payload;
    }
  }

  const clearSelection = (iframe: HTMLIFrameElement | undefined) => {
     selectedElement.value = null;
     if (iframe) {
        iframe.contentWindow?.postMessage({ type: 'VE_CLEAR_SELECTION' }, '*');
     }
  }
  
  const exitEditMode = (iframe: HTMLIFrameElement | undefined) => {
      if (isEditMode.value) {
          isEditMode.value = false;
          selectedElement.value = null;
          iframe?.contentWindow?.postMessage({ type: 'VE_STOP' }, '*');
      }
  }

  onMounted(() => {
    window.addEventListener('message', handleMessage);
  });

  onUnmounted(() => {
    window.removeEventListener('message', handleMessage);
  });

  return {
    isEditMode,
    selectedElement,
    toggleEditMode,
    clearSelection,
    exitEditMode,
    initVisualEditor // exposed in case we need to call it on iframe load
  }
}
