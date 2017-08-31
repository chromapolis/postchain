package com.chromaway.postchain.core

import java.lang.Exception


class ProgrammerError(message: String, cause: Exception? = null) : Exception(message, cause) {

}