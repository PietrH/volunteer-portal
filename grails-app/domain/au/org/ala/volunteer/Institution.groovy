package au.org.ala.volunteer

class Institution implements Serializable {

    Long id
    String name
    String acronym  // optional
    String description // markdown, optional
    String contactName // optional
    String contactEmail // optional
    String contactPhone // optional
    String websiteUrl // optional
    String collectoryUid // optional

    int version

    Date dateCreated
    Date lastUpdated

    static constraints = {
        contactName blank: true, nullable: true
        contactEmail email: true, blank: true, nullable: true
        contactPhone blank: true, nullable: true
        collectoryUid nullable: true, unique: true
        description blank: true, nullable: true, maxSize: 16384
        acronym blank: true, nullable: true
        websiteUrl blank: true, nullable: true
    }

    static mapping = {
        description widget: 'textarea'
    }
}