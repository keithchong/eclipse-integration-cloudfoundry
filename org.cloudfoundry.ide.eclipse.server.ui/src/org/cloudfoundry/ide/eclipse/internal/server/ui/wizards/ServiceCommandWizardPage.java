/*******************************************************************************
 * Copyright (c) 2012 - 2013 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.wizards;

import org.cloudfoundry.ide.eclipse.internal.server.core.ValueValidationUtil;
import org.cloudfoundry.ide.eclipse.internal.server.core.tunnel.ServerService;
import org.cloudfoundry.ide.eclipse.internal.server.core.tunnel.ServiceCommand;
import org.cloudfoundry.ide.eclipse.internal.server.ui.IPartChangeListener;
import org.cloudfoundry.ide.eclipse.internal.server.ui.tunnel.AddCommandDisplayPart;
import org.cloudfoundry.ide.eclipse.internal.server.ui.tunnel.EditCommandDisplayPart;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class ServiceCommandWizardPage extends WizardPage {

	private ServiceCommand serviceCommand;

	private AddCommandDisplayPart displayPart;

	private final ServerService service;

	private final boolean addNewCommand;

	private IStatus partStatus;

	protected ServiceCommandWizardPage(ServerService service, ServiceCommand serviceCommand, boolean addNewCommand) {
		super("Command Page");
		setTitle("Command Definition");
		setDescription("Define a command to launch on a service tunnel");
		this.serviceCommand = serviceCommand;
		this.service = service;
		this.addNewCommand = addNewCommand;
	}

	public void createControl(Composite parent) {
		displayPart = addNewCommand ? new AddCommandDisplayPart(service, serviceCommand) : new EditCommandDisplayPart(
				service, serviceCommand);
		displayPart.addPartChangeListener(new IPartChangeListener() {

			public void handleChange(PartChangeEvent event) {
				if (event != null) {
					partStatus = event.getStatus();
					if (partStatus == null || partStatus.isOK()) {
						setErrorMessage(null);
						setPageComplete(true);
					}
					else {
						if (ValueValidationUtil.isEmpty(partStatus.getMessage())) {
							setErrorMessage(null);
						}
						else {
							setErrorMessage(partStatus.getMessage());
						}
						setPageComplete(false);
					}
				}
			}

		});
		Control control = displayPart.createPart(parent);
		setControl(control);
	}

	@Override
	public boolean isPageComplete() {
		return partStatus == null || partStatus.isOK();
	}

	public ServiceCommand getServiceCommand() {
		if (displayPart != null) {
			serviceCommand = displayPart.getServiceCommand();
		}
		return serviceCommand;
	}

	public boolean applyTerminalToAllCommands() {
		return displayPart != null ? displayPart.applyTerminalToAllCommands() : false;
	}

}