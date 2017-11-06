// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)