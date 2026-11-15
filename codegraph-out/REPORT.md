# CodeGraph Architecture Report

## Architecture Overview

- **Total Nodes:** 5673
- **Total Edges:** 15634
- **Files Count:** 746
- **Graph Density:** 0.0005 (0.05%)

## Node Kind Breakdown

| Kind | Count | Percentage |
| :--- | :---: | :---: |
| Function | 3942 | 69.5% |
| File | 746 | 13.2% |
| Class | 269 | 4.7% |
| Interface | 246 | 4.3% |
| Endpoint | 213 | 3.8% |
| Struct | 186 | 3.3% |
| Table | 69 | 1.2% |
| Enum | 2 | 0.0% |

## Relation Breakdown

| Relation | Count | Percentage |
| :--- | :---: | :---: |
| Calls | 8220 | 52.6% |
| Defines | 4920 | 31.5% |
| Imports | 1950 | 12.5% |
| References | 413 | 2.6% |
| DependsOn | 91 | 0.6% |
| Implements | 40 | 0.3% |

## Top Architectural Hubs (God Nodes)

| Rank | Name | Kind | File | In-Degree | Out-Degree | Score |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: |
| 1 | `t` | Function | `frontend/src/features/translations/views/TranslationManagementView.test.ts` | 202 | 0 | 303.00 |
| 2 | `toUserError` | Function | `frontend/src/lib/api.ts` | 198 | 1 | 298.00 |
| 3 | `toastError` | Function | `frontend/src/lib/toast.ts` | 176 | 1 | 265.00 |
| 4 | `get` | Function | `frontend/src/components/layout/AppShell.vue` | 159 | 0 | 238.50 |
| 5 | `request` | Function | `frontend/src/lib/api.ts` | 158 | 0 | 237.00 |
| 6 | `ok` | Function | `backend/src/main/java/com/furelise/skillmanagement/common/dto/ApiResponse.java` | 151 | 0 | 226.50 |
| 7 | `useI18nStore` | Function | `frontend/src/features/translations/views/TranslationManagementView.test.ts` | 133 | 0 | 199.50 |
| 8 | `User` | Class | `backend/src/main/java/com/furelise/skillmanagement/user/model/User.java` | 119 | 2 | 180.50 |
| 9 | `status` | Function | `backend/src/main/java/com/furelise/skillmanagement/exception/BusinessErrorCode.java` | 118 | 0 | 177.00 |
| 10 | `toastSuccess` | Function | `frontend/src/lib/toast.ts` | 115 | 1 | 173.50 |
| 11 | `getCode` | Function | `backend/src/main/java/com/furelise/skillmanagement/exception/BusinessException.java` | 104 | 0 | 156.00 |
| 12 | `add` | Function | `backend/src/main/java/com/furelise/skillmanagement/mcp/service/McpCommentService.java` | 88 | 0 | 132.00 |
| 13 | `SkillController.java` | File | `backend/src/main/java/com/furelise/skillmanagement/skill/controller/SkillController.java` | 0 | 122 | 122.00 |
| 14 | `allow` | Function | `backend/src/main/java/com/furelise/skillmanagement/common/RateLimiter.java` | 78 | 0 | 117.00 |
| 15 | `email` | Function | `backend/src/test/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchServiceTest.java` | 77 | 0 | 115.50 |

## 🧹 Dead Code & Unused Components (1554 symbols)

The following components have 0 incoming references (in-degree = 0) and are not HTTP endpoints or entry points:

| Symbol | Kind | File | Lines | Out-Degree |
| :--- | :--- | :--- | :---: | :---: |
| `Sitemap_Tree.html` | Class | `Vibe coding basline/Vibe coding basline/Sitemap_Tree.html` | L1-400 | 0 |
| `SkillManagementApplication` | Class | `backend/src/main/java/com/furelise/skillmanagement/SkillManagementApplication.java` | L8-16 | 0 |
| `AiNewsArchiveCleanupScheduler` | Class | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsArchiveCleanupScheduler.java` | L42-118 | 0 |
| `getCron` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L64-66 | 0 |
| `setCron` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L68-70 | 0 |
| `setKnox` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L100-102 | 0 |
| `setMailboxSecrets` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L108-110 | 0 |
| `setAlertEmail` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L116-118 | 0 |
| `Knox` | Class | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L129-355 | 0 |
| `setLoginPath` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L216-218 | 0 |
| `setLoginUserIdField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L224-226 | 0 |
| `setLoginPasswordField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L232-234 | 0 |
| `setLoginStatusField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L240-242 | 0 |
| `setMailListPath` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L248-250 | 0 |
| `setMailReadPath` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L256-258 | 0 |
| `setMhtmlDownloadPath` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L264-266 | 0 |
| `setListItemsField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L280-282 | 0 |
| `setMailIdField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L288-290 | 0 |
| `setMailSeqField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L296-298 | 0 |
| `setFolderIdField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L304-306 | 0 |
| `setSubjectField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L312-314 | 0 |
| `setSenderField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L320-322 | 0 |
| `setSenderNameField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L328-330 | 0 |
| `setDateField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L336-338 | 0 |
| `setBodyHtmlField` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L344-346 | 0 |
| `setDateZone` | Function | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchProperties.java` | L352-354 | 0 |
| `AiNewsBatchScheduler` | Class | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchScheduler.java` | L33-82 | 0 |
| `DeadlineExceeded` | Class | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchService.java` | L247-251 | 0 |
| `SourceResult` | Struct | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchService.java` | L668-669 | 0 |
| `JobRunResult` | Struct | `backend/src/main/java/com/furelise/skillmanagement/ainews/batch/AiNewsBatchService.java` | L672-680 | 0 |

*(and 1524 more symbols...)*
