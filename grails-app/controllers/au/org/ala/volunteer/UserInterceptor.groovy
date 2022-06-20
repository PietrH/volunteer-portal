package au.org.ala.volunteer

import au.org.ala.cas.util.AuthenticationUtils
import org.apache.log4j.Logger

import javax.servlet.http.HttpServletRequest

class UserInterceptor {

    def authService
    private static final Logger logger = Logger.getLogger(UserInterceptor);

    UserInterceptor() {
        matchAll()
    }

    boolean after() {
        if (authService.userId == null) {
            return
        }


        def currentUser = User.findByUserId(authService.userId)
        if (currentUser == null) {
            return
        }

        logger.info("Updating user from auth info...")
        def response = authService.userDetailsClient.getUserDetails(currentUser.userId, true).execute()
        if (response.isSuccessful()) {
            def updatedUser = response.body()
            def firstName = updatedUser.firstName
            def lastName = updatedUser.lastName
            if (firstName != currentUser.firstName || lastName != currentUser.lastName) {
                currentUser.firstName = firstName
                currentUser.lastName = lastName
                currentUser.save()
            }
        } else {
            logger.info("Error fetching user details: ${response.code()}; ${response.errorBody().string()}")
        }

        true
    }

}
