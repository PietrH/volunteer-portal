package au.org.ala.volunteer

class Task implements Serializable {

    def grailsApplication

    //Project project
    String externalIdentifier
    String externalUrl
    String fullyTranscribedBy
    Date dateFullyTranscribed
    String fullyTranscribedIpAddress
    UUID transcribedUUID // unique id for the transcription
    String fullyValidatedBy
    Date dateFullyValidated
    UUID validatedUUID // unique id for the validation
    Boolean isValid
    Integer viewed = -1
    Date created
    Date dateLastUpdated
    Long lastViewed
    String lastViewedBy
    Integer timeToTranscribe
    Integer timeToValidate

    static belongsTo = [project: Project]
    static hasMany = [multimedia: Multimedia, viewedTasks: ViewedTask, fields: Field, comments: TaskComment]

    static mapping = {
        version false
        multimedia cascade: 'all,delete-orphan'
        viewedTasks cascade: 'all,delete-orphan'
        fields cascade: 'all,delete-orphan'
        comments cascade: 'all,delete-orphan'
        transcribedUUID type: 'pg-uuid'
        validatedUUID type: 'pg-uuid'
    }

    static constraints = {
        externalIdentifier nullable: true
        externalUrl nullable: true
        fullyTranscribedBy nullable: true
        dateFullyTranscribed nullable: true
        fullyTranscribedIpAddress nullable: true
        transcribedUUID nullable: true
        fullyValidatedBy nullable: true
        dateFullyValidated nullable: true
        validatedUUID nullable: true
        isValid nullable: true
        viewed nullable: true
        created nullable: true
        dateLastUpdated nullable: true
        lastViewed nullable: true
        lastViewedBy nullable: true
        timeToTranscribe nullable: true
        timeToValidate nullable: true
    }

    // These events use a static method rather than an injected service
    // to prevent issues with serialisation in webflows

    // Executed after an object is persisted to the database
    def afterInsert() {
        GormEventDebouncer.debounceTask(this.id)
    }
    // Executed after an object has been updated
    def afterUpdate() {
        GormEventDebouncer.debounceTask(this.id)
    }
    // Executed after an object has been deleted
    def afterDelete() {
        GormEventDebouncer.debounceDeleteTask(this.id)
    }

    def status(userId) {
        if (fullyValidatedBy != null) return TaskStatus.validated

        if (fullyTranscribedBy != null) return TaskStatus.transcribed

        def timeoutWindow = System.currentTimeMillis() - ((grailsApplication.config.viewedTask.timeout as long) ?: 7200000)
        if (lastViewedBy == null
                || lastViewedBy == userId
                || lastViewed < timeoutWindow) return TaskStatus.open

        else return TaskStatus.currentlyUsed
    }

    def isAvailableForTranscription(userId) {
        return status(userId) == TaskStatus.open
    }

}

enum TaskStatus {
    validated("taskStatus.validated"),
    transcribed("taskStatus.transcribed"),
    currentlyUsed("taskStatus.currentlyUsed"),
    open("taskStatus.open")

    def i18nLabel

    TaskStatus(String i18nLabel) {
        this.i18nLabel = i18nLabel
    }
}