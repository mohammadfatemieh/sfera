package cc.sferalabs.sfera.http.api.rest;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.http.api.HttpApiEvent;

/**
 * <p>
 * API servlet handling requests for events triggering.
 * </p>
 * <p>
 * The triggered events will be instances of {@link HttpApiEvent} and the source
 * node an {@link cc.sferalabs.sfera.http.api.HttpRemoteNode HttpRemoteNode}
 * instance.
 * </p>
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
@SuppressWarnings("serial")
public class EventServlet extends AuthorizedUserServlet {

	public static final String PATH = ApiServlet.PATH + "event";

	@Override
	protected void processAuthorizedRequest(HttpServletRequest req, RestResponse resp)
			throws ServletException, IOException {
		try {
			String id = req.getParameterNames().nextElement();
			String val = req.getParameter(id);
			resp.setAsyncContext(req.startAsync());
			HttpApiEvent remoteEvent = new HttpApiEvent(id, val, req, resp);
			Bus.post(remoteEvent);
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		}
	}

}
