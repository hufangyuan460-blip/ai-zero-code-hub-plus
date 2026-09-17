package com.swu.aiZeroCodeHub.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.mapper.AppMapper;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryQueryRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.entity.ChatHistory;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.ChatHistoryMessageTypeEnum;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    private static final long APP_ID = 3001L;
    private static final long OWNER_ID = 4001L;

    @Mock
    private AppMapper appMapper;

    private ChatHistoryServiceImpl chatHistoryService;
    private App app;
    private User owner;

    @BeforeEach
    void setUp() {
        chatHistoryService = spy(new ChatHistoryServiceImpl());
        ReflectionTestUtils.setField(chatHistoryService, "appMapper", appMapper);

        app = new App();
        app.setId(APP_ID);
        app.setUserId(OWNER_ID);
        app.setPriority(1);
        owner = new User();
        owner.setId(OWNER_ID);
        owner.setUserRole("user");
        when(appMapper.selectOneById(APP_ID)).thenReturn(app);
    }

    @Test
    void ordinaryUserCannotWriteAnotherUsersHistory() {
        User otherUser = new User();
        otherUser.setId(5001L);
        otherUser.setUserRole("user");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatHistoryService.addChatHistory(addRequest(ChatHistoryMessageTypeEnum.USER.getValue(), "hello"), otherUser));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verify(chatHistoryService, never()).save(any(ChatHistory.class));
    }

    @Test
    void ownerCanWriteAndOversizedUserMessageIsRejected() {
        ChatHistoryAddRequest validRequest = addRequest(ChatHistoryMessageTypeEnum.USER.getValue(), "hello");
        stubSuccessfulSave();
        chatHistoryService.addChatHistory(validRequest, owner);
        verify(chatHistoryService).save(any(ChatHistory.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatHistoryService.addChatHistory(addRequest(
                        ChatHistoryMessageTypeEnum.USER.getValue(),
                        "x".repeat(ChatHistoryService.MAX_USER_MESSAGE_LENGTH + 1)), owner));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(chatHistoryService).save(any(ChatHistory.class));
    }

    @Test
    void oversizedAiHistoryIsSavedAsPlaceholder() {
        stubSuccessfulSave();

        chatHistoryService.addChatHistory(addRequest(
                ChatHistoryMessageTypeEnum.AI.getValue(),
                "source".repeat(ChatHistoryService.MAX_AI_HISTORY_LENGTH)), owner);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryService).save(captor.capture());
        assertEquals(ChatHistoryService.OVERSIZED_AI_HISTORY_PLACEHOLDER, captor.getValue().getContent());
    }

    @Test
    void featuredAppDoesNotExposeHistoryToAnotherUserButAdminCanRead() {
        User otherUser = new User();
        otherUser.setId(5001L);
        otherUser.setUserRole("user");
        ChatHistoryQueryRequest query = new ChatHistoryQueryRequest();
        query.setAppId(APP_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatHistoryService.listChatHistoryByPage(query, otherUser));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());

        User admin = new User();
        admin.setId(6001L);
        admin.setUserRole("admin");
        doReturn(new Page<ChatHistory>(1, 10, 0)).when(chatHistoryService)
                .page(any(Page.class), any(QueryWrapper.class));

        assertTrue(chatHistoryService.listChatHistoryByPage(query, admin).getRecords().isEmpty());
        verify(chatHistoryService).page(any(Page.class), any(QueryWrapper.class));
    }

    private ChatHistoryAddRequest addRequest(String messageType, String content) {
        ChatHistoryAddRequest request = new ChatHistoryAddRequest();
        request.setAppId(APP_ID);
        request.setMessageType(messageType);
        request.setContent(content);
        return request;
    }

    private void stubSuccessfulSave() {
        doAnswer(invocation -> {
            ChatHistory history = invocation.getArgument(0);
            history.setId(1L);
            return true;
        }).when(chatHistoryService).save(any(ChatHistory.class));
    }
}
