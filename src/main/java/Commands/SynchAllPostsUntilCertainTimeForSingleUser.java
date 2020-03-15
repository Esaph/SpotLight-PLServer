/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import Esaph.ServerPolicy;
import PLServerMain.PostLocationServer;
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.Timestamp;

@Deprecated
public class SynchAllPostsUntilCertainTimeForSingleUser extends EsaphCommand
{
    private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved WHERE PPID=?";

    private static final String queryGetAllPostsBetweenUserWasSavedLimited = "SELECT * FROM PrivateMoments WHERE ( PrivateMoments.UID = ? AND PrivateMoments.FUID = ? AND PrivateMoments.Time>=?) " +
            "AND EXISTS(SELECT NULL FROM PrivateMomentsSaved WHERE PrivateMoments.PID = PrivateMomentsSaved.PID) " +
            "AND EXISTS ( SELECT NULL FROM Watcher WHERE (( (PrivateMoments.UID = Watcher.UID AND PrivateMoments.FUID = Watcher.FUID) AND ( Watcher.AD = 0 AND Watcher.WF = 0 ) ) OR ( (PrivateMoments.UID = Watcher.FUID AND PrivateMoments.FUID= Watcher.UID) AND ( Watcher.AD = 0 AND Watcher.WF = 0 ) ) ) ) OR ( PrivateMoments.UID = ? AND PrivateMoments.Time>=?) GROUP BY PrivateMoments.PID ORDER BY PrivateMoments.Time DESC LIMIT ?, 30";

    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?";
    private static final String queryGetAllReceivers = "SELECT FUID, State FROM PrivateMoments WHERE PID=? AND UID=?"; //Will not take care of performence.

    public SynchAllPostsUntilCertainTimeForSingleUser(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    private JSONArray getAllReceiversFromPost(String PID) throws Exception //Only my post
    {
        JSONArray jsonArray = new JSONArray();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try
        {
            preparedStatement = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPostsUntilCertainTimeForSingleUser.queryGetAllReceivers);
            preparedStatement.setString(1, PID);
            preparedStatement.setLong(2, super.requestHandler().getThreadUID());
            resultSet = preparedStatement.executeQuery();
            while(resultSet.next())
            {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("REC", resultSet.getLong("FUID"));
                jsonObject.put("ST", resultSet.getShort("State"));
                jsonArray.put(jsonObject);
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("getAllReceiversFromPost() failed: " + ec);
        }
        finally
        {
            if(preparedStatement != null)
            {
                preparedStatement.close();
            }

            if(resultSet != null)
            {
                resultSet.close();
            }
        }
        return jsonArray;
    }

    @Override
    public void run() throws Exception
    {
        PreparedStatement preparedGetChats = null;
        ResultSet resultChats = null;

        try
        {
            long fuid = super.requestHandler().getJSONMessage().getLong("FUSRN");
            long lastPostTime = super.requestHandler().getJSONMessage().getLong("LPT");
            JSONArray jsonArrayMain = new JSONArray();

            if(fuid != super.requestHandler().getThreadUID() && lastPostTime > -1)
            {
                if(fuid > -1)
                {
                    if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
                            super.requestHandler().getThreadUID(),
                            fuid))
                    {
                        int beginChatsCount = super.requestHandler().getJSONMessage().getInt("BEC");

                        preparedGetChats = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPostsUntilCertainTimeForSingleUser.queryGetAllPostsBetweenUserWasSavedLimited);
                        preparedGetChats.setLong(1, super.requestHandler().getThreadUID());
                        preparedGetChats.setLong(2, fuid);
                        preparedGetChats.setTimestamp(3, new Timestamp(lastPostTime));
                        preparedGetChats.setLong(4, super.requestHandler().getThreadUID());
                        preparedGetChats.setTimestamp(5, new Timestamp(lastPostTime));
                        preparedGetChats.setInt(6, beginChatsCount);

                        resultChats = preparedGetChats.executeQuery();
                        JSONArray jsonArrayChatPostings = new JSONArray();

                        while(resultChats.next()) //Query found postings.
                        {
                            JSONObject jsonObject = new JSONObject();

                            PreparedStatement prGetHashtagFromPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPostsUntilCertainTimeForSingleUser.queryGetHashtagsFromPost);
                            prGetHashtagFromPost.setLong(1, resultChats.getLong("PPID"));

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
                            jsonObject.put("ARR_EHT", jsonArrayHashtags);

                            PreparedStatement checkIfSaved = (PreparedStatement)
                                    super.requestHandler()
                                            .getCurrentConnectionToSql()
                                            .prepareStatement(SynchAllPostsUntilCertainTimeForSingleUser.queryGetSaversFromPrivatePost);
                            checkIfSaved.setLong(1, resultChats.getLong("PPID"));

                            ResultSet result = checkIfSaved.executeQuery();
                            JSONArray jsonArray = new JSONArray();
                            while(result.next() && (result.getLong("UID_POST_FROM") == super.requestHandler().getThreadUID() ||
                                    result.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
                            {
                                jsonArray.put(result.getLong("UID_SAVED"));
                            }
                            checkIfSaved.close();
                            result.close();

                            if(super.requestHandler().getThreadUID() == resultChats.getLong("UID"))
                            {
                                jsonObject.put("ARR_REC", getAllReceiversFromPost(resultChats.getString("PID")));
                            }

                            jsonObject.put("PPID", resultChats.getLong("PPID"));
                            jsonObject.put("ARS", jsonArray);
                            jsonObject.put("ABS", resultChats.getLong("UID"));
                            jsonObject.put("EMPF", resultChats.getLong("FUID"));
                            jsonObject.put("PID", resultChats.getString("PID"));
                            jsonObject.put("DES", resultChats.getString("Beschreibung"));
                            jsonObject.put("ST", resultChats.getShort("State"));
                            jsonObject.put("TYPE", resultChats.getShort("TYPE"));
                            jsonObject.put("TIME", resultChats.getTimestamp("TIME").getTime());

                            if(jsonObject.length() > 0)
                            {
                                jsonArrayChatPostings.put(jsonObject);
                            }
                        }
                        jsonArrayMain.put(jsonArrayChatPostings);
                    }
                }
            }

            super.requestHandler().getWriter().println(jsonArrayMain.toString());
        }
        catch (Exception ec)
        {

        }
        finally {
            if(preparedGetChats != null)
            {
                preparedGetChats.close();
            }

            if(resultChats != null)
            {
                resultChats.close();
            }
        }
    }
}
