package com.amazonaws.samples;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import com.amazonaws.services.rekognition.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.model.*;
import com.amazonaws.util.IOUtils;
import com.nmote.iim4j.*;
import com.nmote.iim4j.dataset.*;
import com.nmote.iim4j.serialize.*;
import com.nmote.iim4j.stream.*;

public class Driver {

	public static void main(String[] args) throws Exception {
		
		// PROCESS_PHOTO - Can't be larger than 5MB
		String photoPath = "src/main/resources/test_photo.jpg";
		ByteBuffer imageBytes;
		try (InputStream inputStream = new FileInputStream(new File(photoPath))) {
			imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
		}
				
		// AWS_MOD - Get AWS Credentials
		AWSCredentials credentials;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (/Users/userid/.aws/credentials), and is in a valid format.", e);
		}		

		// AWS_MOD - Configure
		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard().withRegion(Regions.US_WEST_2)
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		
		DetectLabelsRequest request = new DetectLabelsRequest().withImage(new Image().withBytes(imageBytes))
				.withMaxLabels(10).withMinConfidence(7F);

		// AWS_MOD - Call API with results
		DetectLabelsResult awsLabels = awsRekognitionRequest(photoPath, rekognitionClient, request);	
		
		// METADATA_BUILDER
		File originalFile = new File(photoPath);
		File newFile = new File(photoPath + "_modified.jpg");		
		injectMetadata(originalFile, newFile, awsLabels);

	}
	
	public static void injectMetadata(File file, File newFile, DetectLabelsResult awsLabels) throws Exception {

	    IIMFile iimFile = new IIMFile();
	    IIMReader reader = new IIMReader(new JPEGIIMInputStream(new FileIIMInputStream(file)), new IIMDataSetInfoFactory());
	    iimFile.readFrom(reader, 20);     

	    StringBuilder sb = new StringBuilder();		    // Use StringBuilder to build our new meta-data keyword value	    
	    sb = getKeywordMetadata(iimFile, sb);	    			// If meta-data has existing keyword values, save it to StringBuilder	    
		List<Label> labels = awsLabels.getLabels();		// Add keyword values from Amazon Rekognition results
		for (Label label : labels) {
			sb.append(label.getName() + "; ");
		}
	    
	    String tagToAdds = sb.toString();
	    int size = tagToAdds.length();
	    DefaultDataSetInfo valueTag = new DefaultDataSetInfo(537,"Keywords", new StringSerializer(size+ ""),true);
	    DefaultDataSet dataSet = new DefaultDataSet(valueTag, tagToAdds.getBytes());
	    iimFile.add(dataSet); 
	    
	    try (
	    		InputStream in = new BufferedInputStream(new FileInputStream(file));
	    		OutputStream out = new BufferedOutputStream(new FileOutputStream(newFile));
	    	) {
	      JPEGUtil.insertIIMIntoJPEG(out, iimFile, in);		// Inserts new meta-data
	    }	    
	    reader.close();	    
	}

	private static StringBuilder getKeywordMetadata(IIMFile iimFile, StringBuilder sb) throws SerializationException {
		for (Iterator i = iimFile.getDataSets().iterator(); i.hasNext();) {
	        DataSet ds = (DataSet) i.next();            
	        Object value = ds.getValue();
	        if (value instanceof byte[]) {
	            value = "<bytes " + ((byte[]) value).length + ">";
	        }
	        DataSetInfo info = ds.getInfo();	       	      
	        if (info.getDataSetNumber() == 537) {			  
	        		sb.append(value + ";");
	        }
	    }
		return sb;
	}
		
	private static DetectLabelsResult awsRekognitionRequest(String photo, AmazonRekognition rekognitionClient,
			DetectLabelsRequest request) {
		DetectLabelsResult result = null;
		try {
			result = rekognitionClient.detectLabels(request);
			List<Label> labels = result.getLabels();
			System.out.println("Detected labels for " + photo);
			for (Label label : labels) {
				System.out.println(label.getName() + ": " + label.getConfidence().toString());
			}
		} catch (AmazonRekognitionException e) {
			e.printStackTrace();
		}
		return result;
	}
}