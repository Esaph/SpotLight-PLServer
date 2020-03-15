/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import org.json.JSONException;

import Esaph.EsaphInternalMessageCreator;
import Esaph.LogUtilsEsaph;
import Esaph.MessageTypeIdentifier;
import Esaph.SendInformationToUser;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

public class SendUserTyping extends EsaphCommand
{
	public SendUserTyping(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws JSONException
	{
		long ChatPartner = super.requestHandler().getJSONMessage().getLong("CP");


		EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserTyping, ChatPartner).setFireAndForget();

		json.putInto("USRN", super.requestHandler().getThreadUID());

		super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(json.getJSON(),
				super.logUtilsRequest(),
				super.plServer()));
	}
}
