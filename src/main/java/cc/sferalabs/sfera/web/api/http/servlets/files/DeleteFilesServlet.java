package cc.sferalabs.sfera.web.api.http.servlets.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cc.sferalabs.sfera.util.files.FilesUtil;
import cc.sferalabs.sfera.web.api.ErrorMessage;
import cc.sferalabs.sfera.web.api.http.HttpResponse;
import cc.sferalabs.sfera.web.api.http.MissingRequiredParamException;
import cc.sferalabs.sfera.web.api.http.servlets.ApiServlet;
import cc.sferalabs.sfera.web.api.http.servlets.AuthorizedAdminApiServlet;

/**
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
@SuppressWarnings("serial")
public class DeleteFilesServlet extends AuthorizedAdminApiServlet {

	public static final String PATH = ApiServlet.PATH + "files/rm";

	@Override
	protected void processAuthorizedRequest(HttpServletRequest req, HttpResponse resp)
			throws ServletException, IOException {
		try {
			String[] paths = getRequiredParameterValues("path", req, resp);
			List<ErrorMessage> errs = new ArrayList<>();
			for (String path : paths) {
				Path source = Paths.get(".", path);
				if (!FilesUtil.isInRoot(source) || !Files.exists(source)) {
					errs.add(new ErrorMessage(0, "File '" + path + "' not found"));
				} else {
					FilesUtil.delete(source);
				}
			}
			if (errs.isEmpty()) {
				resp.sendResult("ok");
			} else {
				resp.sendErrors(HttpServletResponse.SC_BAD_REQUEST, errs);
				return;
			}
		} catch (MissingRequiredParamException e) {
		}
	}

}