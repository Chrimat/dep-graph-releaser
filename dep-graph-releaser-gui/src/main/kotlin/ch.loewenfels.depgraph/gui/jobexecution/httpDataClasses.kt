package ch.loewenfels.depgraph.gui.jobexecution

data class UsernameAndApiToken(val username: String, val token: String)

data class CrumbWithId(val id: String, val crumb: String)

data class AuthData(val usernameAndApiToken: UsernameAndApiToken, val crumbWithId: CrumbWithId?)