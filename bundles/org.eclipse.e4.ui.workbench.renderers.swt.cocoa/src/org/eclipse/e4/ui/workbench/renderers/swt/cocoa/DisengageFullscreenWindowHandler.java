/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.e4.ui.workbench.renderers.swt.cocoa;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;

/**
 * @since 4.2
 */
public class DisengageFullscreenWindowHandler {
	@CanExecute
	public boolean canExecute(MWindow window) {
		return window.getTags().contains(CocoaUIHandler.TAG_FULLSCREEN);
	}

	@Execute
	public void execute(MWindow window) {
		if (window.getTags().contains(CocoaUIHandler.TAG_FULLSCREEN)) {
			window.getTags().remove(CocoaUIHandler.TAG_FULLSCREEN);
		}
	}

}
