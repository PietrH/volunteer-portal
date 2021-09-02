package au.org.ala.volunteer

import au.org.ala.web.AlaSecured
import java.lang.reflect.Modifier

@AlaSecured(value = ["ROLE_VP_ADMIN"], redirectController = "index")
class SettingController {

    def userService
    def settingsService
    def emailService

    def index() {
        if (!checkAdmin()) {
            return
        }
        def settings = []
        def values = [:]
        def fields = SettingDefinition.class.declaredFields
        fields.each {
            if (Modifier.isStatic(it.modifiers)) {
                if (it.getType().isAssignableFrom(SettingDefinition)) {
                    def settingDef = it.get(null) as SettingDefinition
                    if (settingDef) {
                        settings << settingDef
                        def value = settingsService.getSetting(settingDef)
                        values[settingDef] = value
                    }
                }
            }
        }

        [settings: settings, values: values]

    }

    boolean checkAdmin() {
        if(!userService.isAdmin()) {
            flash.message = "You do not have permission to view this page"
            redirect(url: "/")
            return false
        }
        return true
    }

    def editSetting() {
        if (!checkAdmin()) {
            return
        }
        def key = params.settingKey

        if (!key) {
            redirect(action:'index')
            return
        }

        SettingDefinition settingDefinition = getSettingDefByKey(key)
        def currentValue = settingDefinition ? settingsService.getSetting(settingDefinition) : null

        [settingDefinition: settingDefinition, currentValue: currentValue]
    }

    def saveSetting() {
        if (!checkAdmin()) {
            return
        }
        def key = params.settingKey as String
        def value = params.settingValue as String

        SettingDefinition settingDefinition = getSettingDefByKey(key)
        if (settingDefinition && value) {
            settingsService.setSetting(key, value)
            flash.message= message(code: 'setting.setting_key_to_value', args: [key,value])
        } else {
            flash.message= message(code: 'setting.save_setting_failed', args: [key,value])
        }

        redirect(action:'index')

    }

    private static SettingDefinition getSettingDefByKey(String key) {
        def fields = SettingDefinition.class.declaredFields

        for (def field : fields) {
            if (Modifier.isStatic(field.modifiers)) {
                if (field.getType().isAssignableFrom(SettingDefinition)) {
                    def settingDef = field.get(null) as SettingDefinition
                    if (settingDef) {
                        if (settingDef.key == key) {
                            return settingDef
                        }
                    }
                }
            }
        }
        return null
    }

    def sendTestEmail() {
        if (!checkAdmin()) {
            return
        }
        def to = params.to

        def name = message(code:'default.application.name', default: 'DigiVol')

        if (to) {
            emailService.sendMail(to,"Test message from ${name}", "This is a test message from ${name}.")
            flash.message = message(code: 'setting.sent_a_test_message_to', args: [to])
        }
        redirect(action:'index')
    }
}
