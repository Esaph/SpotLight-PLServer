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

public class SearchHashtags extends EsaphCommand
{
    private static final String querySearchHashtag = "SELECT A.TAG_NAME, (SELECT COUNT(B.TAG_NAME) FROM TAGS AS B WHERE A.TAG_NAME=B.TAG_NAME) AS HUsageCount FROM TAGS AS A WHERE A.TAG_NAME LIKE ? LIMIT 20";

    public SearchHashtags(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        super.requestHandler().returnConnectionToPool();
        while (super.requestHandler().getSocket().isConnected())
        {
            String inputUser = super.requestHandler().readDataCarefully(20);
            inputUser = inputUser
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![");

            PreparedStatement prSearchUser = null;
            ResultSet result = null;
            try
            {
                super.requestHandler().getConnectionToSql();
                prSearchUser = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SearchHashtags.querySearchHashtag);
                prSearchUser.setString(1, "%" + inputUser + "%");
                result = prSearchUser.executeQuery();
                JSONArray jsonArray = new JSONArray();
                while (result.next())
                {
                    JSONObject json = new JSONObject();
                    json.put("TN", result.getString("TAG_NAME"));
                    json.put("CO", result.getInt("HUsageCount"));
                    jsonArray.put(json);
                }
                super.requestHandler().getWriter().println(jsonArray.toString());
                super.requestHandler().returnConnectionToPool();
            }
            catch (Exception ec)
            {
                super.logUtilsRequest().writeLog("Searching hashtags failed: " + ec);
            }
            finally
            {
                if(prSearchUser != null)
                {
                    prSearchUser.close();
                }

                if(result != null)
                {
                    result.close();
                }
            }
        }
    }
}
