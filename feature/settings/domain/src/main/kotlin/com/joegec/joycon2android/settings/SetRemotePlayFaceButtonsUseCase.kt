package com.joegec.joycon2android.settings

class SetRemotePlayFaceButtonsUseCase(private val repository: OutputSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setRemotePlayFaceButtons(enabled)
}
