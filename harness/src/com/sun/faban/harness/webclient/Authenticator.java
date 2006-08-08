/* The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.sun.com/cddl/cddl.html or
 * install_dir/legal/LICENSE
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at install_dir/legal/LICENSE.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: Authenticator.java,v 1.1 2006/08/08 16:56:01 akara Exp $
 *
 * Copyright 2005 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.faban.harness.webclient;

import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;

/**
 * The authenticator handles authentications for the web interface.
 */
public class Authenticator implements CallbackHandler {

    private String login;
    private String passwd;
    private String message;
    private String prompt;


    public Authenticator(String login, String passwd) throws LoginException {
        this.login = login;
        this.passwd = passwd;
        LoginContext loginCtx = new LoginContext("FabanLoginContext", this);
        loginCtx.login();
    }

    public String getMessage() {
        return message;
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * <p> Retrieve or display the information requested in the
     * provided Callbacks.
     * <p/>
     * <p> The <code>handle</code> method implementation checks the
     * instance(s) of the <code>Callback</code> object(s) passed in
     * to retrieve or display the requested information.
     * The following example is provided to help demonstrate what an
     * <code>handle</code> method implementation might look like.
     * This example code is for guidance only.  Many details,
     * including proper error handling, are left out for simplicity.
     * <p/>
     * <pre>
     * public void handle(Callback[] callbacks)
     * throws IOException, UnsupportedCallbackException {
     * <p/>
     * 	 for (int i = 0; i < callbacks.length; i++) {
     * 	    if (callbacks[i] instanceof TextOutputCallback) {
     * <p/>
     * 		// display the message according to the specified type
     * 		TextOutputCallback toc = (TextOutputCallback)callbacks[i];
     * 		switch (toc.getMessageType()) {
     * 		case TextOutputCallback.INFORMATION:
     * 		    System.out.println(toc.getMessage());
     * 		    break;
     * 		case TextOutputCallback.ERROR:
     * 		    System.out.println("ERROR: " + toc.getMessage());
     * 		    break;
     * 		case TextOutputCallback.WARNING:
     * 		    System.out.println("WARNING: " + toc.getMessage());
     * 		    break;
     * 		default:
     * 		    throw new IOException("Unsupported message type: " +
     * 					toc.getMessageType());
     * 		}
     * <p/>
     * 	    } else if (callbacks[i] instanceof NameCallback) {
     * <p/>
     * 		// prompt the user for a username
     * 		NameCallback nc = (NameCallback)callbacks[i];
     * <p/>
     * 		// ignore the provided defaultName
     * 		System.err.print(nc.getPrompt());
     * 		System.err.flush();
     * 		nc.setName((new BufferedReader
     * 			(new InputStreamReader(System.in))).readLine());
     * <p/>
     * 	    } else if (callbacks[i] instanceof PasswordCallback) {
     * <p/>
     * 		// prompt the user for sensitive information
     * 		PasswordCallback pc = (PasswordCallback)callbacks[i];
     * 		System.err.print(pc.getPrompt());
     * 		System.err.flush();
     * 		pc.setPassword(readPassword(System.in));
     * <p/>
     * 	    } else {
     * 		throw new UnsupportedCallbackException
     * 			(callbacks[i], "Unrecognized Callback");
     * 	    }
     * 	 }
     * }
     * <p/>
     * // Reads user password from given input stream.
     * private char[] readPassword(InputStream in) throws IOException {
     *    // insert code to read a user password from the input stream
     * }
     * </pre>
     *
     * @param callbacks an array of <code>Callback</code> objects provided
     *                  by an underlying security service which contains
     *                  the information requested to be retrieved or displayed.
     * @throws java.io.IOException if an input or output error occurs. <p>
     * @throws javax.security.auth.callback.UnsupportedCallbackException
     *                             if the implementation of this
     *                             method does not support one or more of the Callbacks
     *                             specified in the <code>callbacks</code> parameter.
     */
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof TextOutputCallback) {

                // display the message according to the specified type
                TextOutputCallback toc = (TextOutputCallback)callbacks[i];
                switch (toc.getMessageType()) {
                    case TextOutputCallback.INFORMATION:
                        message = toc.getMessage();
                        break;
                    case TextOutputCallback.ERROR:
                        message = "ERROR: " + toc.getMessage();
                        break;
                    case TextOutputCallback.WARNING:
                        message = "WARNING: " + toc.getMessage();
                        break;
                    default:
                        throw new IOException("Unsupported message type: " +
                                toc.getMessageType());
                }

            } else if (callbacks[i] instanceof NameCallback) {

                NameCallback nc = (NameCallback) callbacks[i];
                nc.getPrompt();
                nc.setName(login);

            } else if (callbacks[i] instanceof PasswordCallback) {

                PasswordCallback pc = (PasswordCallback) callbacks[i];
                prompt = pc.getPrompt();
                char[] buffer = new char[passwd.length()];
                passwd.getChars(0, passwd.length(), buffer, 0);
                pc.setPassword(buffer);

            } else {
                throw new UnsupportedCallbackException
                        (callbacks[i], "Unrecognized Callback");
            }
        }
    }
}

