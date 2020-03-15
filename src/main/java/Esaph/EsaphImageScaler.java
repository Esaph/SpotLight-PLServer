/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class EsaphImageScaler
{

	public static File esaphScaleImageForClient(File fileSrc, File destination, Dimension clientViewDims) throws IOException
	{
        BufferedImage bimg = ImageIO.read(fileSrc);
		Dimension dimClientAspectRatio = EsaphImageScaler.getScaledDimension(new Dimension(bimg.getWidth(), bimg.getHeight()), clientViewDims);
		BufferedImage bResized = EsaphImageScaler.resizeImage(bimg, dimClientAspectRatio);
		bResized = cropImage(bResized, clientViewDims);
		ImageIO.write(bResized, "jpg", destination);
		return destination;
	}
	
	private static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) //Need to figure out if the image should grow or get smaller.
	{
	    int original_width = imgSize.width;
	    int original_height = imgSize.height;
	    int bound_width = boundary.width;
	    int bound_height = boundary.height;
	    int new_width = original_width;
	    int new_height = original_height;
	    
	
	    System.out.println("OLD WIDTH: " + original_width + " -- OLD HEIGHT: " + original_height);
	    float aspectRatio = (float) original_width / original_height;
	    System.out.println("RATIO: " + aspectRatio);
	    
	    if(bound_width > bound_height)
    	{
	    	new_width = bound_width;//set to height that he wants
	    	new_height = (int) ((float) bound_width / aspectRatio);
    	}
    	else if(bound_width < bound_height)
    	{
    		new_height = bound_height; //set to width that he wants
		    new_width = (int) ((float) bound_height * aspectRatio);
    	}
    	else
    	{
    	    if(aspectRatio >= 1) //LANDSCAPE
    	    {
    		    System.out.println("RATIO: LANDSCAPE");
    		    new_height = bound_height; //set to width that he wants
    		    new_width = (int) ((float) bound_height * aspectRatio);
    	    }
    	    else
    	    {
    	    	System.out.println("RATIO: PORTRAIT");
    	    	
    	    	new_width = bound_width;//set to height that he wants
    	    	new_height = (int) ((float) bound_width / aspectRatio);
    	    }
    	}
	    
	    System.out.println("NEW WIDTH: " + new_width + " -- NEW HEIGHT: " + new_height);
	    return new Dimension(new_width, new_height);
	}
	
	private static BufferedImage resizeImage(BufferedImage originalImage,
            Dimension dimsScaling) throws IOException
	{
		BufferedImage resizedImage = new BufferedImage(dimsScaling.width, dimsScaling.height, originalImage.getType());
		Graphics2D g = resizedImage.createGraphics();
		g.drawImage(originalImage, 0, 0, dimsScaling.width, dimsScaling.height, null);
		g.dispose();
		return resizedImage;
	}
	
	 private static BufferedImage cropImage(BufferedImage src, Dimension clientSize) //Would be eassier without centriering the crop, just take the standard value - the overadded.
	 {
		 float ratio = (float) src.getWidth() / src.getHeight();
		 int height = 0;
		 int width = 0;
		 
		 System.out.println("CLIENT WIDTH: " + clientSize.width + " -- CLIENT HEIGHT: " + clientSize.height);
		 
		 if(src.getWidth() > src.getHeight()) //CENTER HEIGHT
		 {
			 height = (int) ((src.getHeight() / 2) - (clientSize.getHeight() / 2));
		 }
		 else if(src.getWidth() > src.getHeight()) //CENTER WIDTH
		 {
			 width = (int) ((src.getWidth() / 2) - (clientSize.getWidth() / 2));
		 }
		 else
		 {
			 if(ratio >= 1) //LANDSCAPE
			 {
				 width = (int) ((src.getWidth() / 2) - (clientSize.getWidth() / 2));
			 }
			 else //PORTRAIT
			 {
				 height = (int) ((src.getHeight() / 2) - (clientSize.getHeight() / 2));
			 }
		 }
		 
		 System.out.println("CROP WIDTH: " + width + " -- CROP HEIGHT: " + height);
		 BufferedImage dest = src.getSubimage(width, height, clientSize.width, clientSize.height);
		 return dest;     
	 }
}
