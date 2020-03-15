/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import Esaph.EsaphStoragePaths;
import Esaph.EsaphStoringHandler;
import Esaph.LogUtilsEsaph;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

public class DownloadSticker extends EsaphCommand
{
	public DownloadSticker(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws Exception
	{
		try
		{
			String PID_STICKER = super.requestHandler().getJSONMessage().getString("STID");
			EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();

			File fileSticker = esaphStoringHandler.getStoringFile(PID_STICKER, EsaphStoragePaths.STICKER_FILES_PATH);
			if(fileSticker.exists())
			{
				this.sendStickerFile(fileSticker);
			}
		}
		catch (Exception ec)
		{
			super.logUtilsRequest().writeLog("DownloadSticker failed: " + ec);
		}
	}

	private void sendStickerFile(File file) throws IOException
	{
		super.requestHandler().returnConnectionToPool();
		super.logUtilsRequest().writeLog("SENDING STICKER: " + file.getAbsolutePath());
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
		super.logUtilsRequest().writeLog("Sticker sent.");
		
		fis.close();
		dos.close(); 
	}

}
