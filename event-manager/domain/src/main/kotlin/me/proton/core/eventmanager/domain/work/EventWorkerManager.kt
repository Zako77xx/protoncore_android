/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.eventmanager.domain.work

import me.proton.core.eventmanager.domain.EventManagerConfig
import kotlin.time.Duration

interface EventWorkerManager {

    /**
     * Enqueue a Worker for this [config].
     *
     * @param immediately if true, start/process the task immediately, if possible.
     */
    fun enqueue(config: EventManagerConfig, immediately: Boolean)

    /**
     * Cancel Worker for this [config].
     */
    fun cancel(config: EventManagerConfig)

    /**
     * Returns true if Worker is running.
     */
    suspend fun isRunning(config: EventManagerConfig): Boolean

    /**
     * Get repeat interval while app is in foreground.
     */
    fun getRepeatIntervalForeground(): Duration

    /**
     * Get repeat interval while app is in background.
     */
    fun getRepeatIntervalBackground(): Duration

    /**
     * Get backoff delay.
     */
    fun getBackoffDelay(): Duration
}
