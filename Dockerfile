FROM tomcat:8-jre8
COPY build/libs/volunteer-portal-*.war /usr/local/tomcat/webapps/ROOT.war
COPY context.xml /usr/local/tomcat/conf/context.xml
EXPOSE 8080
