/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EsaphInternalMessageCreator
{
	private JSONObject message;
	public EsaphInternalMessageCreator(String SMCOMMAND, JSONArray jsonArrayReceivers) throws JSONException
	{
		this.message = new JSONObject();
		this.message.put("MCMD", "SE");
		this.message.put("EMPF", jsonArrayReceivers);
		this.message.put("CMD", SMCOMMAND);
	}

	public EsaphInternalMessageCreator(String SMCOMMAND, long RECEIVER_ID) throws JSONException
	{
		this.message = new JSONObject();
		JSONArray jsonArray = new JSONArray();
		JSONObject receiver = new JSONObject();
		receiver.put("REC_ID", RECEIVER_ID);
		jsonArray.put(receiver);

		this.message.put("MCMD", "SE");
		this.message.put("EMPF", jsonArray);
		this.message.put("CMD", SMCOMMAND);
	}

	public EsaphInternalMessageCreator setFireAndForget() throws JSONException
	{
		this.message.put("MCMD", "SEFAF");
		return this;
	}
	
	public <T> void putInto(String KEY, T VALUE) throws JSONException
	{
		this.message.put(KEY, VALUE);
	}
	
	public JSONObject getJSON()
	{
		return this.message;
	}
}
