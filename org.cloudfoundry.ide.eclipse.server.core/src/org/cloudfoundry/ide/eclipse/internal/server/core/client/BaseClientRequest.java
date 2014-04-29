/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. 
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, 
 * Version 2.0 (the "License�); you may not use this file except in compliance 
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 *  Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 ********************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core.client;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudErrorUtil;
import org.cloudfoundry.ide.eclipse.internal.server.core.Messages;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.osgi.util.NLS;

/**
 * Executes Cloud Foundry client ( {@link CloudFoundryOperations} ) operations,
 * and optionally handles reattempts IFF an error is thrown during the
 * operation.
 * <p/>
 * By default, operations are performed only once and any error thrown will not
 * result in further attempts. Subclasses can override this behaviour.
 */
public abstract class BaseClientRequest<T> {

	/**
	 * 
	 */
	private final String label;

	public BaseClientRequest(String label) {
		Assert.isNotNull(label);
		this.label = label;
	}

	/**
	 * 
	 * @return result of client operation
	 * @throws CoreException if failure occurred while attempting to execute the
	 * client operation.
	 */
	public T run(IProgressMonitor monitor) throws CoreException {

		SubMonitor subProgress = SubMonitor.convert(monitor, label, 100);

		CloudFoundryOperations client = getClient(subProgress);
		if (client == null) {
			throw CloudErrorUtil.toCoreException(NLS.bind(Messages.ERROR_NO_CLIENT, label));
		}

		try {
			return runAndWait(client, subProgress);
		}
		catch (CoreException ce) {
			// See if it is a connection error. If so, parse it into readable
			// form.
			String connectionError = CloudErrorUtil.getConnectionError(ce);
			if (connectionError != null) {
				throw CloudErrorUtil.asCoreException(connectionError, ce, true);
			}
			else {
				throw ce;
			}
		}
		finally {
			subProgress.done();
		}

	}

	/**
	 * Performs a client operation, and if necessary, re-attempts the operation
	 * after a certain interval IFF an error occurs based on
	 * {@link #getTotalTimeWait()} and
	 * {@link #getWaitInterval(Throwable, SubMonitor)}.
	 * <p/>
	 * The default behaviour is to only attempt a client operation once and quit
	 * after an error is encountered. Subclasses may modify this behaviour by
	 * overriding {@link #getTotalTimeWait()} and
	 * {@link #getWaitInterval(Throwable, SubMonitor)}
	 * <p/>
	 * Note that reattempts are only decided based on errors thrown by the
	 * client invocation, not by results generated by the client invocation.
	 * @param client client whose operations are invoked. Never null.
	 * @param subProgress
	 * @return result of operation. Can be null.
	 * @throws CoreException if fatal error occurred while performing the
	 * operation (i.e. error that causes the operation to no longer be
	 * reattempted)
	 * @throws OperationCanceledException if further attempts are cancelled even
	 * if time still remains for additional attempts.
	 */
	protected T runAndWait(CloudFoundryOperations client, SubMonitor subProgress) throws CoreException,
			OperationCanceledException {
		Throwable error = null;

		boolean reattempt = true;
		long timeLeft = getTotalTimeWait();

		// Either this operation returns a result during the waiting period or
		// an error occurred, and error
		// gets thrown

		while (reattempt) {

			long interval = -1;

			try {
				return doRun(client, subProgress);
			}
			catch (Throwable e) {
				error = e;
			}

			interval = getWaitInterval(error, subProgress);
			timeLeft -= interval;
			reattempt = !subProgress.isCanceled() && timeLeft >= 0 && interval > 0;
			if (reattempt) {

				try {
					Thread.sleep(interval);
				}
				catch (InterruptedException e) {
					// Ignore, continue with the next iteration
				}
			}
		}

		if (subProgress.isCanceled()) {
			throw new OperationCanceledException();
		}
		else if (error instanceof CoreException) {
			throw (CoreException) error;
		}
		else {
			throw CloudErrorUtil.toCoreException(error);
		}
	}

	/**
	 * Given an error, determine how long the operation should wait before
	 * trying again before timeout is reached. In order for attempt to be tried
	 * again, value must be positive. Any value less than or equal to 0 will not
	 * result in further attempts.
	 * 
	 * <p/>
	 * 
	 * By default it returns -1, meaning that the request is attempted only
	 * once, and any exceptions thrown will not result in reattempts. Subclasses
	 * can override to determine different reattempt conditions.
	 * @param exception to determine how long to wait until another attempt is
	 * made to run the operation. Note that if total timeout time is shorter
	 * than the interval, no further attempts will be made.
	 * @param monitor
	 * @return interval value greater than 0 if attempt is to be made . Any
	 * other value equal or less than 0 will result in the operation terminating
	 * without further reattempts.
	 * @throw CoreException if failed to determine interval. A CoreException
	 * will result in no further attempts.
	 */
	protected long getWaitInterval(Throwable exception, SubMonitor monitor) throws CoreException {
		return -1;
	}

	/**
	 * Perform the actual client operation. The client is guaranteed to be
	 * non-null at this stage.
	 * @param client non-null client
	 * @param progress
	 * @return result of operation.
	 * @throws CoreException
	 */
	protected abstract T doRun(CloudFoundryOperations client, SubMonitor progress) throws CoreException;

	/**
	 * This must never be null. This is the client used to perform operations.
	 * @return Non-null Java client.
	 * @throws CoreException if failed to obtain a client
	 */
	protected abstract CloudFoundryOperations getClient(IProgressMonitor monitor) throws CoreException;

	/**
	 * Total amount of time to wait. If less than the wait interval length, only
	 * one attempt will be made {@link #getWaitInterval(Throwable, SubMonitor)}
	 * @return
	 */
	protected long getTotalTimeWait() {
		return CloudOperationsConstants.DEFAULT_CF_CLIENT_REQUEST_TIMEOUT;
	}

}
