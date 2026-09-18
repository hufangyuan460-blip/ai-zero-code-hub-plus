package com.swu.aiZeroCodeHub.service;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.generation.GenerationRequest;
import com.swu.aiZeroCodeHub.generation.GenerationRunState;
import com.swu.aiZeroCodeHub.generation.GenerationRunStatus;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 生成运行状态服务。
 *
 * <p>状态以 Redis Hash 保存，但每个 Hash 仍严格使用一个
 * {@code agent:run:{runId}} 精确键。状态转移由 Lua 脚本在 Redis 内完成，
 * 避免取消、超时和 SSE 回调之间出现先读后写竞态。</p>
 */
@Service
public class GenerationRunStateService {

    public static final String RUN_KEY_PREFIX = "agent:run:";
    public static final Duration RUN_STATE_TTL = Duration.ofHours(24);

    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'runId', ARGV[1],
                'appId', ARGV[2],
                'userId', ARGV[3],
                'executionMode', ARGV[4],
                'status', 'PENDING',
                'currentStep', ARGV[5],
                'startedAt', ARGV[6],
                'updatedAt', ARGV[6],
                'finishedAt', '',
                'errorMessage', '',
                'retryCount', ARGV[7],
                'maxRetryCount', ARGV[8],
                'cancelRequested', 'false',
                'validationFingerprint', '',
                'seenFingerprints', '|',
                'artifactHash', '',
                'changedFiles', '',
                'validationIssueCount', '-1',
                'llmCallCount', '0',
                'toolCallCount', '0',
                'buildCallCount', '0',
                'repairAttempt', '0',
                'maxRepairAttempts', ARGV[9])
            redis.call('EXPIRE', KEYS[1], ARGV[10])
            return 1
            """, Long.class);

    private static final RedisScript<Long> START_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            local status = redis.call('HGET', KEYS[1], 'status')
            if status ~= 'PENDING' then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'status', 'RUNNING',
                'currentStep', ARGV[1],
                'startedAt', ARGV[2],
                'updatedAt', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    private static final RedisScript<Long> STEP_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return 0
            end
            redis.call('HSET', KEYS[1], 'currentStep', ARGV[1], 'updatedAt', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    private static final RedisScript<Long> RETRY_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'cancelRequested') == 'true' then
                return -2
            end
            local retryCount = tonumber(redis.call('HGET', KEYS[1], 'retryCount') or '0')
            local maxRetryCount = tonumber(redis.call('HGET', KEYS[1], 'maxRetryCount') or '2')
            if retryCount >= maxRetryCount then
                return -3
            end
            retryCount = retryCount + 1
            redis.call('HSET', KEYS[1],
                'retryCount', tostring(retryCount),
                'currentStep', ARGV[1],
                'updatedAt', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return retryCount
            """, Long.class);

    private static final RedisScript<Long> CANCEL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            local storedUserId = redis.call('HGET', KEYS[1], 'userId')
            local storedAppId = redis.call('HGET', KEYS[1], 'appId')
            if storedUserId == false or storedAppId == false then
                return -1
            end
            if storedUserId ~= ARGV[1] and ARGV[2] ~= 'admin' then
                return -2
            end
            local status = redis.call('HGET', KEYS[1], 'status')
            if status ~= 'PENDING' and status ~= 'RUNNING' then
                return -3
            end
            redis.call('HSET', KEYS[1],
                'cancelRequested', 'true',
                'updatedAt', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    private static final RedisScript<Long> FINISH_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return 0
            end
            local finalStatus = ARGV[1]
            local finalError = ARGV[3]
            if redis.call('HGET', KEYS[1], 'cancelRequested') == 'true' then
                finalStatus = 'CANCELLED'
                finalError = '生成任务已取消'
            end
            redis.call('HSET', KEYS[1],
                'status', finalStatus,
                'updatedAt', ARGV[2],
                'finishedAt', ARGV[2],
                'errorMessage', finalError)
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    private static final RedisScript<Long> REPAIR_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then return -1 end
            if redis.call('HGET', KEYS[1], 'cancelRequested') == 'true' then return -2 end
            local seen = redis.call('HGET', KEYS[1], 'seenFingerprints') or '|'
            if string.find(seen, '|' .. ARGV[1] .. '|', 1, true) ~= nil then return -4 end
            local previousHash = redis.call('HGET', KEYS[1], 'artifactHash') or ''
            if previousHash ~= '' and previousHash == ARGV[2] then return -5 end
            local previousIssueCount = tonumber(redis.call('HGET', KEYS[1], 'validationIssueCount') or '-1')
            local issueCount = tonumber(ARGV[4] or '-1')
            -- A clean structural validation (0 issues) may be followed by the
            -- first build-only failure. Once a repair finding exists, later
            -- attempts must strictly reduce its issue count.
            if previousIssueCount > 0 and issueCount >= previousIssueCount then return -6 end
            local attempt = tonumber(redis.call('HGET', KEYS[1], 'repairAttempt') or '0')
            local maxAttempt = tonumber(redis.call('HGET', KEYS[1], 'maxRepairAttempts') or '1')
            if attempt >= maxAttempt then return -3 end
            attempt = attempt + 1
            redis.call('HSET', KEYS[1],
                'validationFingerprint', ARGV[1],
                'seenFingerprints', seen .. ARGV[1] .. '|',
                'artifactHash', ARGV[2],
                'changedFiles', ARGV[3],
                'validationIssueCount', ARGV[4],
                'repairAttempt', tostring(attempt),
                'retryCount', tostring(attempt),
                'currentStep', ARGV[5],
                'updatedAt', ARGV[6])
            redis.call('EXPIRE', KEYS[1], ARGV[7])
            return attempt
            """, Long.class);

    private static final RedisScript<Long> VALIDATION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then return 0 end
            redis.call('HSET', KEYS[1],
                'validationFingerprint', ARGV[1],
                'artifactHash', ARGV[2],
                'changedFiles', ARGV[3],
                'validationIssueCount', ARGV[4],
                'updatedAt', ARGV[5])
            redis.call('EXPIRE', KEYS[1], ARGV[6])
            return 1
            """, Long.class);

    private static final RedisScript<Long> BUDGET_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local status = redis.call('HGET', KEYS[1], 'status')
            if status ~= 'RUNNING' then return -1 end
            if redis.call('HGET', KEYS[1], 'cancelRequested') == 'true' then return -2 end
            local count = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            if count >= tonumber(ARGV[2]) then return 0 end
            count = count + 1
            redis.call('HSET', KEYS[1], ARGV[1], tostring(count), 'updatedAt', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return count
            """, Long.class);

    private static final RedisScript<Long> BUILD_COUNT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then return -1 end
            if redis.call('HGET', KEYS[1], 'cancelRequested') == 'true' then return -2 end
            local count = redis.call('HINCRBY', KEYS[1], 'buildCallCount', 1)
            redis.call('HSET', KEYS[1], 'updatedAt', ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return count
            """, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public GenerationRunState create(GenerationRequest request, int maxRetryCount) {
        return create(request, maxRetryCount, 1);
    }

    public GenerationRunState create(GenerationRequest request, int maxRetryCount, int maxRepairAttempts) {
        if (request == null || request.runId() == null || request.appId() == null || request.userId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成运行信息无效");
        }
        String now = Instant.now().toString();
        int safeMaxRetryCount = Math.min(2, Math.max(0, maxRetryCount));
        int safeMaxRepairAttempts = Math.min(1, Math.max(0, maxRepairAttempts));
        Long result = execute(CREATE_SCRIPT, key(request.runId()),
                request.runId(), String.valueOf(request.appId()), String.valueOf(request.userId()),
                request.executionMode().name(), "排队中", now, "0", String.valueOf(safeMaxRetryCount),
                String.valueOf(safeMaxRepairAttempts), String.valueOf(RUN_STATE_TTL.toSeconds()));
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成运行创建失败");
        }
        return getRequired(request.runId());
    }

    public boolean transitionToRunning(String runId, String currentStep) {
        Long result = execute(START_SCRIPT, key(runId), safeStep(currentStep), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    public boolean updateCurrentStep(String runId, String currentStep) {
        Long result = execute(STEP_SCRIPT, key(runId), safeStep(currentStep), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    /**
     * @return 新的重试次数；-1 表示运行不再活跃，-2 表示已请求取消，-3 表示达到上限。
     */
    public int incrementRetry(String runId, String currentStep) {
        Long result = execute(RETRY_SCRIPT, key(runId), safeStep(currentStep), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return result == null ? -1 : result.intValue();
    }

    /**
     * 记录一次验证结果并进行最多一次、去重后的定向修复预算扣减。
     * -3 达到修复上限，-4 指纹重复，-5 产物未变化，-6 问题未减少，-2 已取消，-1 非运行态。
     */
    public int registerRepair(String runId, String fingerprint, String artifactHash,
                              List<String> changedFiles, String currentStep) {
        return registerRepair(runId, fingerprint, artifactHash, changedFiles, -1, currentStep);
    }

    public int registerRepair(String runId, String fingerprint, String artifactHash,
                              List<String> changedFiles, int issueCount, String currentStep) {
        Long result = execute(REPAIR_SCRIPT, key(runId), safeToken(fingerprint), safeToken(artifactHash),
                safeFiles(changedFiles), String.valueOf(issueCount), safeStep(currentStep), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return result == null ? -1 : result.intValue();
    }

    public boolean updateValidation(String runId, String fingerprint, String artifactHash,
                                    List<String> changedFiles) {
        return updateValidation(runId, fingerprint, artifactHash, changedFiles, -1);
    }

    public boolean updateValidation(String runId, String fingerprint, String artifactHash,
                                    List<String> changedFiles, int issueCount) {
        Long result = execute(VALIDATION_SCRIPT, key(runId), safeToken(fingerprint), safeToken(artifactHash),
                safeFiles(changedFiles), String.valueOf(Math.max(-1, issueCount)), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    public boolean allowLlmCall(String runId, int maxCalls) {
        return allowBudget(runId, "llmCallCount", maxCalls);
    }

    public boolean allowToolCall(String runId, int maxCalls) {
        return allowBudget(runId, "toolCallCount", maxCalls);
    }

    /** Records a real isolated/local build invocation for cost observability. */
    public int recordBuildCall(String runId) {
        Long result = execute(BUILD_COUNT_SCRIPT, key(runId), Instant.now().toString(),
                String.valueOf(RUN_STATE_TTL.toSeconds()));
        return result == null ? -1 : result.intValue();
    }

    private boolean allowBudget(String runId, String field, int maxCalls) {
        Long result = execute(BUDGET_SCRIPT, key(runId), field, String.valueOf(Math.max(0, maxCalls)),
                Instant.now().toString(), String.valueOf(RUN_STATE_TTL.toSeconds()));
        return result != null && result > 0;
    }

    public boolean finish(String runId, GenerationRunStatus desiredStatus, String errorMessage) {
        if (desiredStatus == null || !desiredStatus.isTerminal()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "终态不合法");
        }
        Long result = execute(FINISH_SCRIPT, key(runId), desiredStatus.name(), Instant.now().toString(),
                safeError(errorMessage), String.valueOf(RUN_STATE_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    public CancelResult requestCancel(String runId, User loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            return CancelResult.FORBIDDEN;
        }
        Long result = execute(CANCEL_SCRIPT, key(runId), String.valueOf(loginUser.getId()),
                UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()) ? UserConstant.ADMIN_ROLE : "user",
                Instant.now().toString(), String.valueOf(RUN_STATE_TTL.toSeconds()));
        if (Long.valueOf(1L).equals(result)) {
            return CancelResult.REQUESTED;
        }
        if (Long.valueOf(-1L).equals(result)) {
            return CancelResult.NOT_FOUND;
        }
        if (Long.valueOf(-2L).equals(result)) {
            return CancelResult.FORBIDDEN;
        }
        return CancelResult.NOT_ACTIVE;
    }

    public Optional<GenerationRunState> find(String runId) {
        if (!isValidRunId(runId)) {
            return Optional.empty();
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(key(runId));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new GenerationRunState(
                    value(values, "runId"),
                    Long.valueOf(value(values, "appId")),
                    Long.valueOf(value(values, "userId")),
                    ExecutionModeEnum.valueOf(value(values, "executionMode")),
                    GenerationRunStatus.valueOf(value(values, "status")),
                    value(values, "currentStep"),
                    value(values, "startedAt"),
                    value(values, "updatedAt"),
                    blankToNull(value(values, "finishedAt")),
                    blankToNull(value(values, "errorMessage")),
                    Integer.parseInt(value(values, "retryCount")),
                    Integer.parseInt(value(values, "maxRetryCount")),
                    Boolean.parseBoolean(value(values, "cancelRequested")),
                    blankToNull(value(values, "validationFingerprint")),
                    parseTokens(value(values, "seenFingerprints")),
                    blankToNull(value(values, "artifactHash")),
                    parseFiles(value(values, "changedFiles")),
                    Integer.parseInt(defaultValue(values, "validationIssueCount", "-1")),
                    Integer.parseInt(defaultValue(values, "llmCallCount", "0")),
                    Integer.parseInt(defaultValue(values, "toolCallCount", "0")),
                    Integer.parseInt(defaultValue(values, "buildCallCount", "0")),
                    Integer.parseInt(defaultValue(values, "repairAttempt", "0")),
                    Integer.parseInt(defaultValue(values, "maxRepairAttempts", "1"))
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public GenerationRunState getRequired(String runId) {
        return find(runId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "生成任务不存在"));
    }

    public boolean isCancellationRequested(String runId) {
        return find(runId).map(GenerationRunState::cancelRequested).orElse(false);
    }

    public String key(String runId) {
        if (!isValidRunId(runId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "runId 无效");
        }
        return RUN_KEY_PREFIX + runId;
    }

    private Long execute(RedisScript<Long> script, String key, String... args) {
        return stringRedisTemplate.execute(script, Collections.singletonList(key), (Object[]) args);
    }

    private boolean isValidRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(runId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String safeStep(String step) {
        if (step == null || step.isBlank()) {
            return "处理中";
        }
        return step.length() > 200 ? step.substring(0, 200) : step;
    }

    private String safeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "";
        }
        String sanitized = errorMessage.replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^,;\\s]+", "$1=***")
                .replaceAll("[A-Za-z]:\\\\[^\\n\\r]*|/(?:[^\\n\\r ]+/)+[^\\n\\r ]*", "[路径]");
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }

    private String value(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private String defaultValue(Map<Object, Object> values, String name, String defaultValue) {
        String value = value(values, name);
        return value.isBlank() ? defaultValue : value;
    }

    private Set<String> parseTokens(String value) {
        if (value == null || value.isBlank() || "|".equals(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split("\\|"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> parseFiles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .filter(file -> !file.isBlank())
                .toList();
    }

    private String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "").replaceAll("[^a-zA-Z0-9._:-]", "_");
    }

    private String safeFiles(List<String> files) {
        if (files == null) {
            return "";
        }
        return files.stream()
                .filter(file -> file != null && !file.isBlank())
                .map(file -> file.replace("|", "").replace("\\", "/"))
                .filter(file -> !file.startsWith("/")
                        && !file.matches("^[A-Za-z]:/.*")
                        && !file.contains(".."))
                .limit(100)
                .collect(Collectors.joining("|"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public enum CancelResult {
        REQUESTED,
        NOT_FOUND,
        FORBIDDEN,
        NOT_ACTIVE
    }
}
