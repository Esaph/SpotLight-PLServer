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

public class AllowOrDisallowToSeeOwnPost extends EsaphCommand
{
    private static final String queryGetUserPrivatePostInChatContext = "SELECT * FROM (SELECT * FROM PrivateMoments WHERE PPID=?) AS P JOIN PrivateReceivers ON P.PPID=PrivateReceivers.PPID AND PrivateReceivers.UID_REC=? LIMIT 1";
    private static final String queryIsPostSavedByUser = "SELECT * FROM PrivateMomentsSaved WHERE UID_POST_FROM=? AND UID_SAVED=? AND PPID=?";
    private static final String queryInsertPostSaved = "INSERT INTO PrivateMomentsSaved (UID_POST_FROM, UID_SAVED, PPID) values (?, ?, ?)";
    private static final String queryRemoveFromSavedPosts = "DELETE FROM PrivateMomentsSaved WHERE UID_SAVED=? AND PPID=?";
    private static final String queryLookUpIfPostSavedWith24HoursCheck = "SELECT * FROM PrivateMoments WHERE PPID=? AND TIME <= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND NOT EXISTS(SELECT NULL FROM PrivateMomentsSaved WHERE PrivateMoments.PPID = PrivateMomentsSaved.PPID)";
    private static final String queryDeletePrivatePostComplete = "DELETE FROM PrivateMoments WHERE UID=? AND PPID=?";
    private static final String queryDeleteAllHashtagsFromPost = "DELETE FROM TAGS WHERE PPID=?";
    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?";
    private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved WHERE PPID=?";


    public AllowOrDisallowToSeeOwnPost(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        int status = -1;
        long RECEIVER_ID = super.requestHandler().getJSONMessage().getLong("REC_ID");
        if(RECEIVER_ID > -1)
        {
            PreparedStatement prLookUpPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryGetUserPrivatePostInChatContext);
            prLookUpPost.setLong(1, super.requestHandler().getJSONMessage().getLong("PPID"));
            prLookUpPost.setLong(2, RECEIVER_ID);
            ResultSet resultPostLookUp = prLookUpPost.executeQuery();

            if(resultPostLookUp.next())
            {
                if(resultPostLookUp.getLong("UID") != super.requestHandler().getThreadUID())
                    return;

                PreparedStatement prCheckIfSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryIsPostSavedByUser);
                prCheckIfSaved.setLong(1, super.requestHandler().getThreadUID());
                prCheckIfSaved.setLong(2, RECEIVER_ID);
                prCheckIfSaved.setLong(3, super.requestHandler().getJSONMessage().getLong("PPID"));
                ResultSet resultCheckFirst = prCheckIfSaved.executeQuery();
                if(resultCheckFirst.next()) //Was saved, so unsave
                {
                    PreparedStatement preparedUnSaveSomeonesPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryRemoveFromSavedPosts);
                    preparedUnSaveSomeonesPost.setLong(1, RECEIVER_ID);
                    preparedUnSaveSomeonesPost.setLong(2, super.requestHandler().getJSONMessage().getLong("PPID"));
                    preparedUnSaveSomeonesPost.executeUpdate();
                    preparedUnSaveSomeonesPost.close();

                    EsaphInternalMessageCreator esaphCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserDisallowedYouToSeeHimPostInPrivate,
                            RECEIVER_ID);
                    esaphCreator.putInto("PPID", super.requestHandler().getJSONMessage().getLong("PPID"));
                    esaphCreator.putInto("FUSRN", super.requestHandler().getThreadUID());
                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphCreator.getJSON(), super.logUtilsRequest(), super.plServer()));


                    PreparedStatement preparedStatementCheckIfNoOneSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryLookUpIfPostSavedWith24HoursCheck);
                    preparedStatementCheckIfNoOneSaved.setLong(1, super.requestHandler().getJSONMessage().getLong("PPID"));
                    ResultSet resultSetStillSaved = preparedStatementCheckIfNoOneSaved.executeQuery();
                    if(resultSetStillSaved.next())
                    {
                        EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
                        File fileHQ = esaphStoringHandler.getStoringFile(resultPostLookUp.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

                        if(fileHQ != null)
                        {
                            fileHQ.delete();
                        }

                        PreparedStatement preparedStatementRemoveHashtags = //Removing all Hashtags.
                                (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                                        .prepareStatement(AllowOrDisallowToSeeOwnPost.queryDeleteAllHashtagsFromPost);
                        preparedStatementRemoveHashtags.executeUpdate();
                        preparedStatementRemoveHashtags.close();

                        PreparedStatement removePostInPrivateComplete = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryDeletePrivatePostComplete);
                        removePostInPrivateComplete.setLong(1, super.requestHandler().getThreadUID());
                        removePostInPrivateComplete.setLong(2, super.requestHandler().getJSONMessage().getLong("PPID"));
                        removePostInPrivateComplete.executeUpdate();
                        removePostInPrivateComplete.close();
                    }
                    resultSetStillSaved.close();
                    preparedStatementCheckIfNoOneSaved.close();
                    status = 1;
                }
                else
                {
                    PreparedStatement preparedSaveSomeonesPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryInsertPostSaved);
                    preparedSaveSomeonesPost.setLong(1, super.requestHandler().getThreadUID());
                    preparedSaveSomeonesPost.setLong(2, RECEIVER_ID);
                    preparedSaveSomeonesPost.setLong(3, super.requestHandler().getJSONMessage().getLong("PPID"));
                    preparedSaveSomeonesPost.executeUpdate();
                    preparedSaveSomeonesPost.close();

                    PreparedStatement prGetHashtagFromPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(AllowOrDisallowToSeeOwnPost.queryGetHashtagsFromPost);
                    prGetHashtagFromPost.setLong(1, resultPostLookUp.getLong("PPID"));
                    ResultSet resultHashtags = prGetHashtagFromPost.executeQuery();
                    JSONArray jsonArrayHashtags = new JSONArray();
                    while(resultHashtags.next())
                    {
                        JSONObject json = new JSONObject();
                        json.put("TAG", resultHashtags.getString("TAG_NAME"));
                        jsonArrayHashtags.put(json);
                    }
                    prGetHashtagFromPost.close();
                    resultHashtags.close();

                    PreparedStatement checkIfSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                            .prepareStatement(AllowOrDisallowToSeeOwnPost.queryGetSaversFromPrivatePost);
                    checkIfSaved.setLong(1, resultPostLookUp.getLong("PPID"));

                    ResultSet result = checkIfSaved.executeQuery();

                    JSONArray jsonArraySaved = new JSONArray();
                    while(result.next() && (result.getLong("UID_POST_FROM") == super.requestHandler().getThreadUID() ||
                            result.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
                    {
                        jsonArraySaved.put(result.getLong("UID_SAVED"));
                    }
                    checkIfSaved.close();
                    result.close();

                    JSONArray jsonArrayReceiver = new JSONArray(); //Creating the receiver array.
                    JSONObject jsonObjectSingleReceiver = new JSONObject();
                    jsonObjectSingleReceiver.put("ST", resultPostLookUp.getShort("State"));
                    jsonObjectSingleReceiver.put("EMPF", resultPostLookUp.getLong("UID_REC"));
                    jsonArrayReceiver.put(jsonObjectSingleReceiver);

                    EsaphInternalMessageCreator esaphCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserAllowedYouToSeeHimPostInPrivate,
                            RECEIVER_ID);
                    esaphCreator.putInto("PPID", resultPostLookUp.getLong("PPID"));
                    esaphCreator.putInto("USRN", super.requestHandler().getThreadUID());
                    esaphCreator.putInto("PID", resultPostLookUp.getString("PID"));
                    esaphCreator.putInto("DES", resultPostLookUp.getString("Beschreibung"));
                    esaphCreator.putInto("ARS", jsonArraySaved);
                    esaphCreator.putInto("FT", resultPostLookUp.getShort("TYPE"));
                    esaphCreator.putInto("ARR_EHT", jsonArrayHashtags);
                    esaphCreator.putInto("ARR_REC", jsonArrayReceiver);
                    esaphCreator.putInto("TIME_POST", resultPostLookUp.getTimestamp("Time").getTime());
                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphCreator.getJSON(), super.logUtilsRequest(), super.plServer()));
                    status = 2;
                }
                resultCheckFirst.close();
                prCheckIfSaved.close();
            }
            prLookUpPost.close();
            resultPostLookUp.close();

            super.requestHandler().getWriter().println(status);
        }
    }
}
