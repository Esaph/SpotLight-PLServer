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
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.ResultSet;

public class SynchAllPrivateChatPosts extends EsaphCommand
{
    private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved s WHERE s.PPID=? AND (s.UID_SAVED=? OR s.UID_SAVED=?) LIMIT 2"; //We can limit this by 2, because only max.2 rows will be found.
    //This query loads specific in a context, that only one saved think exists, beause the in database on device there would the posts get refues. You know what i mean. So one save for single user, to get other savings we have to synchronisate other contacts.
    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?";

    private static final String queryGetAllPostsBetweenUserWasSavedLimited = "\n" +
            "\n" +
            "SELECT\n" +
            "   * \n" +
            "FROM\n" +
            "   (\n" +
            "      SELECT\n" +
            "         * \n" +
            "      FROM\n" +
            "         PrivateMoments \n" +
            "      WHERE\n" +
            "         (\n" +
            "            UID = ? \n" +
            "            OR UID = ?\n" +
            "         )\n" +
            "         AND EXISTS\n" +
            "         (\n" +
            "            SELECT\n" +
            "               NULL \n" +
            "            FROM\n" +
            "               PrivateMomentsSaved \n" +
            "            WHERE\n" +
            "               PrivateMoments.PPID = PrivateMomentsSaved.PPID\n" +
            "         )\n" +
            "         AND EXISTS\n" +
            "         (\n" +
            "            SELECT\n" +
            "               NULL \n" +
            "            FROM\n" +
            "               Watcher \n" +
            "            WHERE\n" +
            "               (\n" +
            "( (PrivateMoments.UID = Watcher.UID \n" +
            "                  AND Watcher.FUID=?) \n" +
            "                  AND \n" +
            "                  (\n" +
            "                     Watcher.AD = 0 \n" +
            "                     AND Watcher.WF = 0 \n" +
            "                  )\n" +
            ") \n" +
            "                  OR \n" +
            "                  (\n" +
            "(PrivateMoments.UID = Watcher.FUID \n" +
            "                     AND Watcher.UID=?) \n" +
            "                     AND \n" +
            "                     (\n" +
            "                        Watcher.AD = 0 \n" +
            "                        AND Watcher.WF = 0 \n" +
            "                     )\n" +
            "                  )\n" +
            "               )\n" +
            "         )\n" +
            "         OR \n" +
            "         (\n" +
            "            PrivateMoments.UID = ? AND EXISTS\n" +
            "         (\n" +
            "            SELECT\n" +
            "               NULL \n" +
            "            FROM\n" +
            "               PrivateMomentsSaved \n" +
            "            WHERE\n" +
            "               PrivateMoments.PPID = PrivateMomentsSaved.PPID\n" +
            "         )\n" +
            "         )\n" +
            "      ORDER BY\n" +
            "         PrivateMoments.Time DESC "+
            "   )\n" +
            "   AS tablePosts \n" +
            "   JOIN\n" +
            "      PrivateReceivers \n" +
            "      ON (PrivateReceivers.PPID = tablePosts.PPID AND tablePosts.UID=? AND PrivateReceivers.UID_REC=?) OR (PrivateReceivers.PPID = tablePosts.PPID AND PrivateReceivers.UID_REC=?) ORDER BY tablePosts.Time DESC LIMIT ?,20" +
            "\n";

    //Above Statement gets all posts, with all receivers from database. Not supporting currently joining the hashtag table.
    //Query joins the saved table to check for the row if the user has saved it.

    public SynchAllPrivateChatPosts(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        PreparedStatement preparedGetChats = null;
        ResultSet resultChats = null;

        try
        {
            long fuid = super.requestHandler().getJSONMessage().getLong("FUSRN");

            if(fuid > -1)
            {
                int beginChatsCount = super.requestHandler().getJSONMessage().getInt("BEC");

                preparedGetChats = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPrivateChatPosts.queryGetAllPostsBetweenUserWasSavedLimited);
                preparedGetChats.setLong(1, super.requestHandler().getThreadUID()); //UID
                preparedGetChats.setLong(2, fuid);
                preparedGetChats.setLong(3, super.requestHandler().getThreadUID());
                preparedGetChats.setLong(4, super.requestHandler().getThreadUID());
                preparedGetChats.setLong(5, super.requestHandler().getThreadUID()); //UID
                preparedGetChats.setLong(6, super.requestHandler().getThreadUID()); //UID
                preparedGetChats.setLong(7, fuid);
                preparedGetChats.setLong(8, super.requestHandler().getThreadUID()); //UID
                preparedGetChats.setInt(9, beginChatsCount); //UID


                resultChats = preparedGetChats.executeQuery();
                JSONArray jsonArrayChatPostings = new JSONArray();

                while(resultChats.next()) //Query found postings.
                {
                    JSONObject jsonObjectPost = new JSONObject();

                    PreparedStatement checkIfSaved = (PreparedStatement) //Putting saved thinks.
                            super.requestHandler()
                                    .getCurrentConnectionToSql()
                                    .prepareStatement(SynchAllPrivateChatPosts.queryGetSaversFromPrivatePost);
                    checkIfSaved.setLong(1, resultChats.getLong("PPID"));
                    checkIfSaved.setLong(2, resultChats.getLong("UID"));
                    checkIfSaved.setLong(3, resultChats.getLong("UID_REC"));

                    ResultSet resultSaved = checkIfSaved.executeQuery();

                    JSONArray jsonArraySaved = new JSONArray();
                    while(resultSaved.next())
                    {
                        if((resultSaved.getLong("UID_POST_FROM") == super.requestHandler().getThreadUID() ||
                                resultSaved.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
                        {
                            JSONObject jsonObjectSaveUserInfo = new JSONObject();
                            jsonObjectSaveUserInfo.put("UID_SAVED", resultSaved.getLong("UID_SAVED"));
                            jsonArraySaved.put(jsonObjectSaveUserInfo);
                        }
                    }
                    checkIfSaved.close();
                    resultSaved.close();

                    if(jsonArraySaved.length() == 0) //Oben die sql anweisung, liefert alle beiträge die gespeichert wurden.
                        //Es kann sein, das der Beitrag nicht von gespeichert wurde, aber trotzdem drin ist.

                    {
                        continue;
                    }


                    PreparedStatement prGetHashtagFromPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPrivateChatPosts.queryGetHashtagsFromPost);
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

                    jsonObjectPost.put("ARS", jsonArraySaved);
                    jsonObjectPost.put("ARR_EHT", jsonArrayHashtags);
                    jsonObjectPost.put("PPID", resultChats.getLong("PPID"));
                    jsonObjectPost.put("ABS", resultChats.getLong("UID"));
                    jsonObjectPost.put("PID", resultChats.getString("PID"));
                    jsonObjectPost.put("TYPE", resultChats.getShort("TYPE"));
                    jsonObjectPost.put("DES", resultChats.getString("Beschreibung"));
                    jsonObjectPost.put("TIME", resultChats.getTimestamp("TIME").getTime());

                    JSONArray jsonArrayReceivers = new JSONArray();


                    JSONObject jsonObjectReceiver = new JSONObject();
                    jsonObjectReceiver.put("REC_ID", resultChats.getLong("UID_REC"));
                    jsonObjectReceiver.put("ST", (short) resultChats.getShort("State"));
                    jsonArrayReceivers.put(jsonObjectReceiver);

                    jsonObjectPost.put("ARR_REC", jsonArrayReceivers);
                    jsonArrayChatPostings.put(jsonObjectPost);
                }

                super.requestHandler().getWriter().println(jsonArrayChatPostings.toString());
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("SynchAllPrivateChatPosts() failed: " + ec);
        }
        finally
        {
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
