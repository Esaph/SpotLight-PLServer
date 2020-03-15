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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeleteProfilbild extends EsaphCommand
{
    public DeleteProfilbild(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        try
        {
            EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
            File fileProfilbildStoringPath = esaphStoringHandler.getStoringFile(Long.toString(super.requestHandler().getThreadUID()), EsaphStoragePaths.PATH_PROFILBILDER);
            if(fileProfilbildStoringPath.exists())
            {
                if(fileProfilbildStoringPath.delete())
                {
                    EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_PB_REMOVE,
                            getMyFriends());
                    esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                    esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());

                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(),
                            super.logUtilsRequest(),
                            super.plServer()));

                    super.requestHandler().getWriter().println("1");
                }
                else
                {
                    super.requestHandler().getWriter().println("0");
                }
            }
            else
            {
                super.requestHandler().getWriter().println("0");
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("DeleteProfilbild() failed: " + ec);
        }
    }


    private static final String queryUserFriends = "SELECT * FROM Watcher WHERE UID=? AND AD=0 OR FUID=? AND AD=0";
    private JSONArray getMyFriends() throws SQLException
    {
        JSONArray friendUid = new JSONArray();
        PreparedStatement preparedStatementGetFriends = null;
        ResultSet resultGetFriends = null;
        try
        {
            preparedStatementGetFriends = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                    .prepareStatement(DeleteProfilbild.queryUserFriends);
            preparedStatementGetFriends.setLong(1, super.requestHandler().getThreadUID());
            preparedStatementGetFriends.setLong(2, super.requestHandler().getThreadUID());
            resultGetFriends = preparedStatementGetFriends.executeQuery();

            while (resultGetFriends.next())
            {
                JSONObject jsonObject = new JSONObject();
                if (resultGetFriends.getLong("UID") == super.requestHandler().getThreadUID())
                {
                    jsonObject.put("REC_ID", resultGetFriends.getLong("FUID"));
                }
                else {
                    jsonObject.put("REC_ID", resultGetFriends.getLong("UID"));
                }
                friendUid.put(jsonObject);
            }
            return friendUid;
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("getMyFriends() failed: " + ec);
            return new JSONArray();
        }
        finally
        {
            if(preparedStatementGetFriends != null)
            {
                preparedStatementGetFriends.close();
            }

            if(resultGetFriends != null)
            {
                resultGetFriends.close();
            }
        }
    }

}
