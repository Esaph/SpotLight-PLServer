/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import PLServerMain.PostLocationServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Deprecated
public class SynchHashtags extends EsaphCommand
{
    private static final String QUERY_GET_HASHTAG_NAMES_LIMITED = "SELECT * FROM TAGS WHERE UID_POST_FROM=? GROUP BY TAG_NAME ORDER BY COUNT(TAG_NAME) LIMIT ?, 30";
    private static final String QUERY_GET_POST_BY_PID = "SELECT * FROM PrivateMoments WHERE PID=? LIMIT 1";
    private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved WHERE PPID=?";
    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?";

    public SynchHashtags(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        int startFromHashtags = super.requestHandler().getJSONMessage().getInt("BEH");
        if(startFromHashtags < 0)
            return;

        PreparedStatement preparedStatement = super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchHashtags.QUERY_GET_HASHTAG_NAMES_LIMITED);
        preparedStatement.setLong(1, super.requestHandler().getThreadUID());
        preparedStatement.setInt(2, startFromHashtags);
        ResultSet resultSet = preparedStatement.executeQuery();

        JSONArray jsonArray = new JSONArray();

        while(resultSet.next())
        {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("TN", resultSet.getString("TAG_NAME"));
            jsonObject.put("PID", resultSet.getString("PID"));
            jsonObject.put("PF", resultSet.getLong("UID_POST_FROM"));
            //jsonObject.put("PH", getPostFromPID(resultSet.getString("PID"), startFromAllConversationPosts));
            jsonArray.put(jsonObject);
        }

        resultSet.close();
        preparedStatement.close();

        super.requestHandler().getWriter().println(jsonArray.toString());
    }


    private JSONObject getPostFromPID(String PID, int startFromPosts) throws Exception
    {
        JSONObject jsonObject = new JSONObject();

        PreparedStatement preparedStatementLookUpPost = super
                .requestHandler()
                .getCurrentConnectionToSql()
                .prepareStatement(SynchHashtags.QUERY_GET_POST_BY_PID);
        preparedStatementLookUpPost.setString(1, PID);
        ResultSet resultPostLookUp = preparedStatementLookUpPost.executeQuery();
        if(resultPostLookUp.next())
        {
            PreparedStatement prGetHashtagFromPost = (PreparedStatement)
                    super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchHashtags.queryGetHashtagsFromPost);
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
            jsonObject.put("ARR_EHT", jsonArrayHashtags);

            PreparedStatement checkIfSaved = (PreparedStatement)
                    super.requestHandler()
                            .getCurrentConnectionToSql()
                            .prepareStatement(SynchHashtags.queryGetSaversFromPrivatePost);
            checkIfSaved.setLong(1, resultPostLookUp.getLong("PPID"));

            ResultSet result = checkIfSaved.executeQuery();
            JSONArray jsonArray = new JSONArray();
            while(result.next() && (result.getLong("UID_POST_FROM") == super.requestHandler().getThreadUID() ||
                    result.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
            {
                jsonArray.put(result.getLong("UID_SAVED"));
            }
            checkIfSaved.close();
            result.close();
            jsonObject.put("ARS", jsonArray);

            jsonObject.put("PPID", resultPostLookUp.getLong("PPID"));
            jsonObject.put("ABS", resultPostLookUp.getLong("UID"));
            jsonObject.put("EMPF", resultPostLookUp.getLong("FUID"));
            jsonObject.put("PID", resultPostLookUp.getString("PID"));
            jsonObject.put("DES", resultPostLookUp.getString("Beschreibung"));
            jsonObject.put("ST", resultPostLookUp.getShort("State"));
            jsonObject.put("TYPE", resultPostLookUp.getShort("TYPE"));
            jsonObject.put("TIME", resultPostLookUp.getTimestamp("TIME").getTime());
        }
        preparedStatementLookUpPost.close();
        resultPostLookUp.close();
        return jsonObject;
    }
}
