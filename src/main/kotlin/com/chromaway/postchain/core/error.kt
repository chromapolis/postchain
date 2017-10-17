package com.chromaway.postchain.core

class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)