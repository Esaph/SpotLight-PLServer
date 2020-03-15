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
import java.sql.ResultSet;

import com.mysql.jdbc.PreparedStatement;
import Esaph.LogUtilsEsaph;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

public class DownloadAudioFile extends EsaphCommand
{
	private static final String QUERY_GET_AUDIO_PATH = "SELECT AUDIO_PATH, UID_EMPFANGER FROM Memo WHERE AID=? AND (UID_EMPFANGER=? OR UID_ABSENDER=?) LIMIT 1";
	private static final String QUERY_DELETE_AUDIO = "DELETE FROM Memo WHERE AID=? AND (UID_EMPFANGER=? OR UID_ABSENDER=?)";
	private boolean deleteOnSend = false;
	
	public DownloadAudioFile(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest, boolean deleteOnSend)
	{
		super(plServer, requestHandler, logUtilsRequest);
		this.deleteOnSend = deleteOnSend;
	}

	@Override
	public void run() throws Exception
	{
		PreparedStatement prGetAudio = null;
		ResultSet result = null;
		try
		{
			prGetAudio =
				(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(DownloadAudioFile.QUERY_GET_AUDIO_PATH);
			prGetAudio.setString(1, super.requestHandler().getJSONMessage().getString("AID"));
			prGetAudio.setLong(2, super.requestHandler().getThreadUID()); //Ich muss empfänger sein, sicherheitsmechanismus falls jemand die AID hat.
			prGetAudio.setLong(3, super.requestHandler().getThreadUID());
			result = prGetAudio.executeQuery();
			if(result.next())
			{
				File fileToSendAudio = new File(result.getString("AUDIO_PATH"));
				this.sendAudioFile(fileToSendAudio);
				if(this.deleteOnSend && super.requestHandler().getThreadUID() == result.getLong("UID_EMPFANGER")) //delete only, if reciever has downloaded it.
				{
					PreparedStatement prDeleteAudio =
							(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(DownloadAudioFile.QUERY_DELETE_AUDIO);
					prDeleteAudio.setString(1, super.requestHandler().getJSONMessage().getString("AID"));
					prDeleteAudio.setLong(2, super.requestHandler().getThreadUID()); //Ich muss empfänger sein, sicherheitsmechanismus falls jemand die AID hat.
					prDeleteAudio.setLong(3, super.requestHandler().getThreadUID());
					int resultDelete = prDeleteAudio.executeUpdate();
					if(resultDelete > 0)
					{
						fileToSendAudio.delete();
					}
					prDeleteAudio.close();
				}
			}
		}
		catch (Exception ec)
		{
		}
		finally
		{
			if(prGetAudio != null)
			{
				prGetAudio.close();
			}

			if(result != null)
			{
				result.close();
			}
		}


	}
	
	
	private void sendAudioFile(File file) throws IOException
	{
		super.requestHandler().returnConnectionToPool();
		super.logUtilsRequest().writeLog("SENDING Audio: " + file.getAbsolutePath());
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
		super.logUtilsRequest().writeLog("Audio sent.");
		
		fis.close();
		dos.close(); 
	}

}
