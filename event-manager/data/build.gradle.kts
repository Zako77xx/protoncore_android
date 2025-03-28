/*
 * Copyright (c) 2023 Proton AG
 * This file is part of Proton AG and ProtonCore.
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
import studio.forface.easygradle.dsl.android.*

plugins {
    protonAndroidLibrary
    protonDagger
    kotlin("plugin.serialization")
}

protonBuild {
    apiModeDisabled()
}

protonCoverage {
    minBranchCoveragePercentage.set(39)
    minLineCoveragePercentage.set(55)
}

protonDagger {
    workManagerHiltIntegration = true
}

publishOption.shouldBePublishedAsLib = true

android {
    namespace = "me.proton.core.eventmanager.data"
}

dependencies {
    api(
        project(Module.accountManagerDomain),
        project(Module.data),
        project(Module.dataRoom),
        project(Module.domain),
        project(Module.eventManagerDomain),
        project(Module.kotlinUtil),
        project(Module.networkData),
        project(Module.presentation),
        `android-work-runtime`,
        `serialization-core`,
        `hilt-androidx-workManager`,
        retrofit
    )

    implementation(
        project(Module.accountDomain),
        project(Module.accountManagerPresentation),
        project(Module.networkDomain),
        project(Module.userData),

        `android-work-runtime`,
        `coroutines-core`,
        `lifecycle-common`,
        `room-ktx`
    )

    testImplementation(
        project(Module.contactData),
        project(Module.keyData),
        project(Module.kotlinTest),
        junit,
        `kotlin-test`,
        mockk
    )
}
