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
import PLServerMain.PostLocationServer.RequestHandler;

public abstract class EsaphCommand
{
	private LogUtilsEsaph logUtilsRequest;
	private RequestHandler requestHandler;
	private PostLocationServer plServer;
	
	public EsaphCommand(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		this.plServer = plServer;
		this.requestHandler = requestHandler;
		this.logUtilsRequest = logUtilsRequest;
	}
	
	public RequestHandler requestHandler()
	{
		return this.requestHandler;
	}
	
	public PostLocationServer plServer()
	{
		return this.plServer;
	}
	
	public LogUtilsEsaph logUtilsRequest()
	{
		return this.logUtilsRequest;
	} 
	
	public abstract void run() throws Exception;
	
}
