/*
 * Copyright (c) 2020 Proton Technologies AG
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

import studio.forface.easygradle.dsl.*

plugins {
    protonKotlinLibrary
}

protonCoverage {
    minBranchCoveragePercentage.set(0)
    minLineCoveragePercentage.set(4)
}

protonBuild {
    apiModeDisabled()
}

publishOption.shouldBePublishedAsLib = true

dependencies {
    api(
        project(Module.cryptoCommon),
        project(Module.domain),
        project(Module.eventManagerDomain),
        project(Module.keyDomain),
        `ez-vcard`,
        `javax-inject`
    )

    implementation(
        `coroutines-core`
    )

    testImplementation(
        project(Module.kotlinTest),
        junit,
        `kotlin-test`,
        mockk,
    )
}
