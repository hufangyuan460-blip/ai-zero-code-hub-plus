# AI 生成网页：按类型解析并保存到本地

## 参与角色

- Facade：AiCodeGeneratorFacade
- 解析：CodeParserExecutor → (HtmlCodeParser / MultiFileCodeParser)
- 保存：CodeFileSaverExecutor → (HtmlCodeFileSaverTemplate / MultiFileCodeFileSaverTemplate)
- 输出目录：CodeOutputProperties（code.output.root-dir）

## 非流式链路

```text
Facade.generateAndSaveCode(userMessage, type)
  -> AiCodeGeneratorService.generate*(...)
  -> CodeFileSaverExecutor.save(result, type)
  -> Template.save(...)  (建目录 + 写文件)
```

## 流式链路

```text
Facade.generateAndSaveCodeStream(userMessage, type) -> Flux<String>
  doOnNext: 累计 chunk
  doOnComplete:
    -> CodeParserExecutor.parse(raw, type)
    -> CodeFileSaverExecutor.save(result, type)
```

## 解析规则（简）

- HTML：优先提取 ```html 代码块；否则用原文兜底
- 多文件：提取 html/css/js 代码块；html 缺失时用原文兜底，css/js 缺失为空字符串

## 扩展新类型（最小改动）

1) CodeGenTypeEnum 增加枚举
2) 新增 Result implements CodeResult
3) 新增 Parser implements CodeParserStrategy
4) 新增 SaverTemplate extends AbstractCodeFileSaverTemplate
5) Facade 增加生成分支（非流式/流式任选）
