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

public class SyncStickerPacks extends EsaphCommand
{
    private static final String QUERY_GET_STICKER_PACKS_LIMITED = "SELECT * FROM StickerPack WHERE UID_CREATOR=? ORDER BY CREATED DESC LIMIT ?, 5";
    private static final String QUERY_GET_ALL_STICKER_FROM_PACK = "SELECT * FROM Stickers WHERE LSPID=? ORDER BY CREATED DESC";

    public SyncStickerPacks(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        PreparedStatement prGetStickerPacks = null;
        ResultSet result = null;

        try
        {
            prGetStickerPacks =
                    (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SyncStickerPacks.QUERY_GET_STICKER_PACKS_LIMITED);
            prGetStickerPacks.setLong(1, super.requestHandler().getThreadUID());
            prGetStickerPacks.setInt(2, super.requestHandler().getJSONMessage().getInt("POS"));
            result = prGetStickerPacks.executeQuery();
            JSONArray jsonStickerPacks = new JSONArray();
            while(result.next())
            {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("CR", super.requestHandler().getThreadUID());
                jsonObject.put("PN", result.getString("PACK_NAME"));
                jsonObject.put("LSPID", result.getString("LSPID"));
                jsonObject.put("CT", result.getTimestamp("CREATED").getTime());

                JSONArray jsonArrayStickersFromStickerPack = new JSONArray();

                PreparedStatement prGetAllStickersFromPack =
                        (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SyncStickerPacks.QUERY_GET_ALL_STICKER_FROM_PACK);
                prGetAllStickersFromPack.setString(1, result.getString("LSPID"));
                ResultSet resultStickersFromPack = prGetAllStickersFromPack.executeQuery();

                while(resultStickersFromPack.next())
                {
                    JSONObject sticker = new JSONObject();
                    sticker.put("LSID", resultStickersFromPack.getString("LSID"));
                    sticker.put("STID", resultStickersFromPack.getString("STICKER_PATH"));
                    sticker.put("CT", resultStickersFromPack.getTimestamp("CREATED").getTime());
                    sticker.put("CR", resultStickersFromPack.getLong("UID_CREATOR"));
                    jsonArrayStickersFromStickerPack.put(sticker);
                }
                resultStickersFromPack.close();
                prGetAllStickersFromPack.close();

                jsonObject.put("STA", jsonArrayStickersFromStickerPack);
                jsonStickerPacks.put(jsonObject);
            }
            super.requestHandler().getWriter().println(jsonStickerPacks.toString());
        }
        catch (Exception ec)
        {

        }
        finally
        {
            if(prGetStickerPacks != null)
            {
                prGetStickerPacks.close();
            }

            if(result != null)
            {
                result.close();
            }
        }
    }
}
