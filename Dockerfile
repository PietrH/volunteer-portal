FROM tomcat:8-jre8
COPY build/libs/volunteer-portal-*.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
