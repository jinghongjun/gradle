/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.daemon.server.health;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE;

/** An expiry strategy which only triggers when system memory falls below a threshold. */
public class LowMemoryDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private final MemoryInfo memoryInfo;
    protected final long memoryThresholdInBytes;
    private static final Logger LOG = Logging.getLogger(LowMemoryDaemonExpirationStrategy.class);

    public static final String EXPIRATION_REASON = "to reclaim system memory";

    // Reasonable default threshold bounds: between 384M and 1G
    public static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
    public static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

    private LowMemoryDaemonExpirationStrategy(MemoryInfo memoryInfo, long memoryThresholdInBytes) {
        Preconditions.checkArgument(memoryThresholdInBytes >= 0);
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.memoryThresholdInBytes = normalizeThreshold(memoryThresholdInBytes, MIN_THRESHOLD_BYTES, MAX_THRESHOLD_BYTES);
    }

    private long normalizeThreshold(final long thresholdIn, final long minValue, final long maxValue) {
        return Math.min(maxValue, Math.max(minValue, thresholdIn));
    }

    /**
     * Creates an expiration strategy which expires the daemon when free memory drops below the specific % of total.
     *
     * @param minFreeMemoryPercentage when free memory drops below this percentage between 0 and 1, the daemon will expire.
     */
    public static LowMemoryDaemonExpirationStrategy belowFreePercentage(double minFreeMemoryPercentage) {
        return belowFreePercentage(minFreeMemoryPercentage, new MemoryInfo());
    }

    @VisibleForTesting
    static LowMemoryDaemonExpirationStrategy belowFreePercentage(double minFreeMemoryPercentage, MemoryInfo memInfo) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");

        return new LowMemoryDaemonExpirationStrategy(
            memInfo,
            (long) (memInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage));
    }

    public DaemonExpirationResult checkExpiration() {
        long freeMem = memoryInfo.getFreePhysicalMemory();
        if (freeMem < memoryThresholdInBytes) {
            LOG.info("after free system memory (" + NumberUtil.formatBytes(freeMem) + ") fell below threshold of " + NumberUtil.formatBytes(memoryThresholdInBytes));
            return new DaemonExpirationResult(GRACEFUL_EXPIRE, EXPIRATION_REASON);
        } else {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }
    }
}
