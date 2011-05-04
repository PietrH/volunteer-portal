class BootStrap {

    javax.sql.DataSource dataSource

    def init = { servletContext ->

      //add a utility method for creating a map from a arraylist
      java.util.ArrayList.metaClass.toMap = { ->
        def myMap = [:]
        delegate.each { keyCount ->
          myMap.put keyCount[0], keyCount[1]
        }
        myMap
      }

      //DDL - add cascading, currently not working from generated DDL from grails
      def sql = new groovy.sql.Sql(dataSource)
      sql.execute("""
          alter table multimedia drop constraint FK4B39F64BCBAB13A;
          alter table picklist_item drop constraint FKE7584B1388EA2EFA;
          alter table project drop constraint FKED904B19AD0C811A;
          alter table task drop constraint FK363585D7B4217A;
          alter table viewed_task drop constraint FK2B205EE0CBAB13A;
          alter table task add constraint FK363585D7B4217A
              foreign key (project_id) references project ON DELETE CASCADE;
          alter table multimedia add constraint FK4B39F64BCBAB13A
              foreign key (task_id) references task ON DELETE CASCADE;
          alter table picklist_item add constraint FKE7584B1388EA2EFA
              foreign key (picklist_id) references picklist ON DELETE CASCADE;
          alter table viewed_task add constraint FK2B205EE0CBAB13A
              foreign key (task_id) references task ON DELETE CASCADE;
        """)
    }
    def destroy = {
    }
}
