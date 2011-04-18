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
 * $Id$
 *
 * Copyright 2005-2009 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.faban.driver;

/**
 * DefinitionException reports an error in the BenchmarkDefinition,
 * BenchmarkDriver, and BenchmarkOperation annotations.
 *
 * @author Akara Sucharitakul
 */
public class DefinitionException extends Exception {

	private static final long serialVersionUID = 2L;

    public DefinitionException() {
        super();
    }

    public DefinitionException(Throwable cause) {
        super(cause);
    }

    public DefinitionException(Throwable cause, String messageFmtString, Object... args) {
        super(String.format(messageFmtString, args), cause);
    }

    public DefinitionException(String message, Object... args) {
        super(String.format(message, args));
    }
}
