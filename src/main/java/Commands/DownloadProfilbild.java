/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.*;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class DownloadProfilbild extends EsaphCommand
{
	public DownloadProfilbild(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws Exception
	{
		File sonderAnfertigung = null;
		try
		{
			// TODO: 03.03.2019 warning, check if no one can sent radom path for pid, to get data which he isnt allowed to.

			EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();

			File file = esaphStoringHandler.getStoringFile(super.requestHandler().getJSONMessage().getString("PID"), EsaphStoragePaths.PATH_PROFILBILDER); //PID IS THE UID OF THE USER.
			if(file != null)
			{
				if(super.requestHandler().getJSONMessage().has("VW") && super.requestHandler().getJSONMessage().has("VH"))
				{
					int viewsWidth = super.requestHandler().getJSONMessage().getInt("VW");
					int viewsHeight = super.requestHandler().getJSONMessage().getInt("VH");
					sonderAnfertigung = EsaphImageScaler.esaphScaleImageForClient(file,
							esaphStoringHandler.getTempFile(EsaphDataPrefix.JPG_PREFIX, super.requestHandler().getThreadUID()),
							new Dimension(viewsWidth, viewsHeight));

					if(sonderAnfertigung.exists())
					{
						this.sendImage(sonderAnfertigung);
						sonderAnfertigung.delete();
					}
				}
				else
				{
					this.sendImage(file);
				}
			}
		}
		catch (Exception ec)
		{
			super.logUtilsRequest().writeLog("DownloadProfilbild() failed: " + ec);
		}
		finally {
			if(sonderAnfertigung != null)
			{
				sonderAnfertigung.delete();
			}
		}
	}

	private void sendImage(File file) throws IOException
	{
		super.requestHandler().returnConnectionToPool();
		super.logUtilsRequest().writeLog("SENDING PROFILBILD: " + file.getAbsolutePath());
		DataOutputStream dos = new DataOutputStream(super.requestHandler().getSocket().getOutputStream());
		FileInputStream fis = new FileInputStream(file);
		
		long length = file.length();
		super.requestHandler().getWriter().println(length);
		byte[] buffer = new byte[4096];
		while (fis.read(buffer) > 0)
		{
			dos.write(buffer);
			dos.flush();
		}
		super.logUtilsRequest().writeLog("Profilbild sent.");
		
		fis.close();
		dos.close(); 
	}

}
