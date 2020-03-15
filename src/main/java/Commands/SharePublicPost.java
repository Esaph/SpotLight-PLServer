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

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SharePublicPost extends EsaphCommand
{
    private static final String QUERY_INSERT_SHARED = "INSERT INTO SharedPublic (UID_SHARED, UID_SHARED_TO, UID_POST_FROM, PID) values (?, ?, ?, ?)";
    private static final String QUERYS_GET_PUBLIC_POST_BY_PID = "SELECT * FROM PublicPosts WHERE PID=? LIMIT 1";

    public SharePublicPost(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        JSONArray mainArrayShareTo = new JSONArray(super.requestHandler().getJSONMessage().get("WAMP").toString());


        PreparedStatement preparedStatementPost = null;
        ResultSet resultSetPost = null;

        try
        {
            preparedStatementPost = (PreparedStatement)
            super.requestHandler().getCurrentConnectionToSql()
            .prepareStatement(SharePublicPost.QUERYS_GET_PUBLIC_POST_BY_PID);
            preparedStatementPost.setString(1, super.requestHandler().getJSONMessage().getString("PID"));
            resultSetPost = preparedStatementPost.executeQuery();

            if(resultSetPost.next())
            {
                JSONArray jsonArrayReceiversUID = new JSONArray();
                JSONArray jsonArrayReceiversUsernames = new JSONArray();

                for(int counter = 0; counter < mainArrayShareTo.length(); counter++)
                {
                    long FUID = mainArrayShareTo.getLong(counter);

                    PreparedStatement preparedStatementInsertNew = null;
                    try
                    {
                        if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
                                super.requestHandler().getThreadUID(), FUID) != ServerPolicy.POLICY_CASE_ALLOWED)
                            continue;


                        preparedStatementInsertNew = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                                .prepareStatement(SharePublicPost.QUERY_INSERT_SHARED);
                        preparedStatementInsertNew.setLong(1, super.requestHandler().getThreadUID());
                        preparedStatementInsertNew.setLong(2, FUID);
                        preparedStatementInsertNew.setLong(3, resultSetPost.getLong("UID"));
                        preparedStatementInsertNew.setString(4, resultSetPost.getString("PID"));
                        int success = preparedStatementInsertNew.executeUpdate();

                        if(success >= 1)
                        {
                            JSONObject jsonObjectReceiver = new JSONObject();
                            jsonObjectReceiver.put("REC_ID", FUID);
                            jsonArrayReceiversUID.put(jsonObjectReceiver);
                            jsonArrayReceiversUsernames.put(mainArrayShareTo.get(counter));
                        }
                    }
                    catch (Exception ec)
                    {
                    }
                    finally
                    {
                        if(preparedStatementInsertNew != null)
                        {
                            preparedStatementInsertNew.close();
                        }
                    }
                }


                EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_SHARED_PUBLIC_POST,
                        jsonArrayReceiversUID); //You dont need to check if there are friends again. This happens magically in message server
                esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());
                esaphInternalMessageCreator.putInto("USRNPF", resultSetPost.getLong("UID"));
                esaphInternalMessageCreator.putInto("PID", resultSetPost.getString("PID"));


                super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(),
                        super.logUtilsRequest(),
                        super.plServer()));


                EsaphInternalMessageCreator esaphInternalMessageCreatorPostOwner = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_YOUR_PUBLIC_POST_WAS_SHARED,
                        resultSetPost.getLong("UID")); //You dont need to check if there are friends again. This happens magically in message server
                esaphInternalMessageCreatorPostOwner.putInto("USNR", super.requestHandler().getThreadUID());
                esaphInternalMessageCreatorPostOwner.putInto("TIME", System.currentTimeMillis());
                esaphInternalMessageCreatorPostOwner.putInto("PID", resultSetPost.getString("PID"));
                esaphInternalMessageCreatorPostOwner.putInto("ARR_SH_TO", jsonArrayReceiversUsernames); //Look up usernames.

                super.requestHandler().getWriter().println("1");
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("SharePublicPost() failed: " + ec);
        }
        finally {

            if(preparedStatementPost != null)
            {
                preparedStatementPost.close();
            }

            if(resultSetPost != null)
            {
                resultSetPost.close();
            }
        }
    }
}
