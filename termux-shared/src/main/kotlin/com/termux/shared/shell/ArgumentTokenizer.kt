/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2010, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 *
 * This file is part of DrJava.  Download the current version of this project
 * from http://www.drjava.org/ or http://sourceforge.net/projects/drjava/
 *
 * END_COPYRIGHT_BLOCK*/

package com.termux.shared.shell

import java.util.LinkedList

/**
 * Utility class which can tokenize a String into a list of String arguments,
 * with behavior similar to parsing command line arguments to a program.
 * Quoted Strings are treated as single arguments, and escaped characters
 * are translated so that the tokenized arguments have the same meaning.
 * Since all methods are static, the class is declared abstract to prevent
 * instantiation.
 * @version $Id$
 */
object ArgumentTokenizer {
    private const val NO_TOKEN_STATE = 0
    private const val NORMAL_TOKEN_STATE = 1
    private const val SINGLE_QUOTE_STATE = 2
    private const val DOUBLE_QUOTE_STATE = 3

    /**
     * Tokenizes the given String into String tokens
     * @param arguments A String containing one or more command-line style arguments to be tokenized.
     * @return A list of parsed and properly escaped arguments.
     */
    @JvmStatic
    fun tokenize(arguments: String): List<String> {
        return tokenize(arguments, false)
    }

    /**
     * Tokenizes the given String into String tokens.
     * @param arguments A String containing one or more command-line style arguments to be tokenized.
     * @param stringify whether or not to include escape special characters
     * @return A list of parsed and properly escaped arguments.
     */
    @JvmStatic
    fun tokenize(arguments: String, stringify: Boolean): List<String> {
        val argList = LinkedList<String>()
        var currArg = StringBuilder()
        var escaped = false
        var state = NO_TOKEN_STATE // start in the NO_TOKEN_STATE
        val len = arguments.length

        // Loop over each character in the string
        var i = 0
        while (i < len) {
            val c = arguments[i]
            if (escaped) {
                // Escaped state: just append the next character to the current arg.
                escaped = false
                currArg.append(c)
            } else {
                when (state) {
                    SINGLE_QUOTE_STATE -> {
                        if (c == '\'') {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE
                        } else {
                            currArg.append(c)
                        }
                    }
                    DOUBLE_QUOTE_STATE -> {
                        if (c == '"') {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE
                        } else if (c == '\\') {
                            // Look ahead, and only escape quotes or backslashes
                            i++
                            val next = arguments[i]
                            if (next == '"' || next == '\\') {
                                currArg.append(next)
                            } else {
                                currArg.append(c)
                                currArg.append(next)
                            }
                        } else {
                            currArg.append(c)
                        }
                    }
                    NO_TOKEN_STATE, NORMAL_TOKEN_STATE -> {
                        when (c) {
                            '\\' -> {
                                escaped = true
                                state = NORMAL_TOKEN_STATE
                            }
                            '\'' -> {
                                state = SINGLE_QUOTE_STATE
                            }
                            '"' -> {
                                state = DOUBLE_QUOTE_STATE
                            }
                            else -> {
                                if (!Character.isWhitespace(c)) {
                                    currArg.append(c)
                                    state = NORMAL_TOKEN_STATE
                                } else if (state == NORMAL_TOKEN_STATE) {
                                    // Whitespace ends the token; start a new one
                                    argList.add(currArg.toString())
                                    currArg = StringBuilder()
                                    state = NO_TOKEN_STATE
                                }
                            }
                        }
                    }
                    else -> throw IllegalStateException("ArgumentTokenizer state $state is invalid!")
                }
            }
            i++
        }

        // If we're still escaped, put in the backslash
        if (escaped) {
            currArg.append('\\')
            argList.add(currArg.toString())
        } else if (state != NO_TOKEN_STATE) {
            // Close the last argument if we haven't yet
            argList.add(currArg.toString())
        }

        // Format each argument if we've been told to stringify them
        if (stringify) {
            for (j in argList.indices) {
                argList[j] = "\"" + escapeQuotesAndBackslashes(argList[j]) + "\""
            }
        }
        return argList
    }

    /**
     * Inserts backslashes before any occurrences of a backslash or
     * quote in the given string.  Also converts any special characters
     * appropriately.
     */
    @JvmStatic
    internal fun escapeQuotesAndBackslashes(s: String): String {
        val buf = StringBuilder(s)

        // Walk backwards, looking for quotes or backslashes.
        //  If we see any, insert an extra backslash into the buffer at
        //  the same index.  (By walking backwards, the index into the buffer
        //  will remain correct as we change the buffer.)
        for (i in s.length - 1 downTo 0) {
            val c = s[i]
            when {
                c == '\\' || c == '"' -> {
                    buf.insert(i, '\\')
                }
                c == '\n' -> {
                    buf.deleteCharAt(i)
                    buf.insert(i, "\\n")
                }
                c == '\t' -> {
                    buf.deleteCharAt(i)
                    buf.insert(i, "\\t")
                }
                c == '\r' -> {
                    buf.deleteCharAt(i)
                    buf.insert(i, "\\r")
                }
                c == '\b' -> {
                    buf.deleteCharAt(i)
                    buf.insert(i, "\\b")
                }
                c == '\u000C' -> { // \f form feed
                    buf.deleteCharAt(i)
                    buf.insert(i, "\\f")
                }
            }
        }
        return buf.toString()
    }
}
