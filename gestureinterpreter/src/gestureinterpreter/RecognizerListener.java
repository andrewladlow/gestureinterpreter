package gestureinterpreter;

import com.leapmotion.leap.Controller;
import com.leapmotion.leap.Listener;
import com.leapmotion.leap.Frame;
import com.leapmotion.leap.Hand;
import com.leapmotion.leap.HandList;
import com.leapmotion.leap.Finger;
import com.leapmotion.leap.FingerList;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import com.leapmotion.leap.Screen;
import com.leapmotion.leap.Vector;
import com.leapmotion.leap.Bone.Type;
import com.leapmotion.leap.Pointable.Zone;

import javafx.geometry.Point2D;

public class RecognizerListener extends Listener {
	
	private boolean touchedBack = false;	
	private BooleanProperty frameReady = new SimpleBooleanProperty();	
	private Gesture gesture;	
    private int gestureFrameCount = 0;  
    private int minGestureFrames = 5;
    private int minGestureVelocity = 300;    
    private int poseFrameCount = 0;
    private int minPoseFrames = 50;
    private int maxPoseVelocity = 30;	
    private boolean validPoseFrame = false;
    private boolean validPose = false;   
    private long timeRecognized = 0;   
    private State state;   
    private ArrayList<Gesture> storedGestures;   
    private PDollarRecognizer pdRec = new PDollarRecognizer();   
    private final RecognizerGUI recGUI;   

    private enum State {
    	IDLE, RECORDING;
    }
    

	public BooleanProperty frameReadyProperty() {
		return frameReady;
	}
    
    public RecognizerListener(RecognizerGUI main) {
    	this.recGUI = main;
    	storedGestures = new ArrayList<Gesture>();
    	gesture = new Gesture("testGesture");
    	state = State.IDLE;
    	File[] files = new File("gestures/").listFiles();
    	for (File file : files) {
	    	try {
	    		FileInputStream inStream = new FileInputStream(file);
	    		ObjectInputStream ObjInStream = new ObjectInputStream(inStream);
	    		Gesture tempGesture = (Gesture) ObjInStream.readObject();
	    		
	    		System.out.println(Arrays.asList(tempGesture.getPointArray().size()));
/*	    		for (Point p : tempGesture.getPointArray()) {
	    			System.out.println("PRE NORMALIZED");
	    			System.out.println("X: " + p.getX() + "Y: " + p.getY() + "Z: " + p.getZ());
	    		}*/
	 		
	    		tempGesture.setPointArray(PDollarRecognizer.Resample(tempGesture.getPointArray(), PDollarRecognizer.mNumPoints));
	    		tempGesture.setPointArray(PDollarRecognizer.Scale(tempGesture.getPointArray()));
	    		tempGesture.setPointArray(PDollarRecognizer.TranslateTo(tempGesture.getPointArray(), new Point(0.0,0.0,0.0)));
	    		
	    		storedGestures.add(tempGesture);
	    		
	    		System.out.println(Arrays.asList(tempGesture.getPointArray().size()));
/*	    		for (Point p : tempGesture.getPointArray()) {
	    			System.out.println("NOW NORMALIZED");
	    			System.out.println("X: " + p.getX() + "Y: " + p.getY() + "Z: " + p.getZ());
	    		}*/
	 		
	    		
	    		
	    		ObjInStream.close();
	    		inStream.close();
	    	} catch (Exception e) {
	    		e.printStackTrace();
	    	}
    	}
    }
    
    public void onConnect(Controller controller) {
    	System.out.println("connected recognizer");
    }
    
    public void onExit(Controller controller) {
    	System.out.println("disconnected recognizer");
    }
	
	public void onFrame(Controller controller) {
		validPoseFrame = false;
		Frame frame = controller.frame();
		frameReady.set(false);
		if (!frame.hands().isEmpty()) {
			frameReady.set(true);			
			Finger frontFinger = frame.fingers().frontmost();
			Vector frontFingerTip = frontFinger.tipPosition();
			
			if (frontFingerTip.getZ() < -85) {
				if (frontFingerTip.getY() > 10 && frontFingerTip.getY() < 90) {
					if (frontFingerTip.getX() > -170 && frontFingerTip.getX() < 30) {					
						if (!touchedBack) {
							if (frontFinger.touchZone() == Zone.ZONE_TOUCHING) {
								touchedBack = true;
								recGUI.backButton.touchStatusProperty().set(true);
							}
						}
					}
				}
			}
					
			if (touchedBack && frontFinger.touchZone() != Zone.ZONE_TOUCHING) {
				touchedBack = false;
				recGUI.backButton.touchStatusProperty().set(false);
			    recGUI.goBack();
			} 
					
			// enforce delay between recognitions
			if (System.currentTimeMillis() - timeRecognized > 500) {	        
		        if (validFrame(frame, minGestureVelocity, maxPoseVelocity)) {	            		             
		            if (state == State.IDLE) {
		            	gestureFrameCount = 0;
		                state = State.RECORDING; 
		            }      
	                gestureFrameCount++;
	                System.out.println("gesture frame count: " + gestureFrameCount);
		            storePoint(frame);
		            
		        } 
		        else if (state == State.RECORDING) {
		            System.out.println("debug record fail state");
		            state = State.IDLE;
		            
		            if (validPose || (gestureFrameCount >= minGestureFrames)) {
		            	if (validPose) {
		            		gesture.setType("pose");
		            	}
		            	else {
		            		gesture.setType("gesture");
		            	}
		            	System.out.println("debug recognize");
		                RecognizerResults recResult = pdRec.Recognize(gesture, storedGestures);
		                System.out.println("\nClosest match: " + recResult.getName() + "\nNormalized score: " + recResult.getScore());
		                timeRecognized = System.currentTimeMillis();
		                Platform.runLater(() -> {
				            recGUI.gestureRecognitionProperty().set(recResult);
		                });
		                // reset variables
		                validPoseFrame = false;
		                validPose = false;
		                gestureFrameCount = 0;
		                poseFrameCount = 0;
			            state = State.IDLE; 
		            } else {
		            	System.out.println("Recognition failed");
		            }
		        }
			}
		} 
		else {
			if (state != State.IDLE) {
				state = State.IDLE;
			}
		}
	}

    public Boolean validFrame(Frame frame, int minVelocity, int maxVelocity) {
        
        for (Hand hand : frame.hands()) {	
            Vector palmVelocityTemp = hand.palmVelocity(); 
            float palmVelocity = Math.max(Math.abs(palmVelocityTemp.getX()), 
            							  Math.max(Math.abs(palmVelocityTemp.getY()), 
            									   Math.abs(palmVelocityTemp.getZ())));             
            
            if (palmVelocity >= minVelocity) {
            	return true;
            } 
            else if (palmVelocity <= maxVelocity) {
            	validPoseFrame = true;
            	System.out.println("valid palm pose");
            	break;
            }
                  
            for (Finger finger : hand.fingers()) {           	 
            	Vector fingerVelocityTemp = finger.tipVelocity();
                float fingerVelocity = Math.max(Math.abs(fingerVelocityTemp.getX()), 
                								Math.max(Math.abs(fingerVelocityTemp.getY()), 
                										 Math.abs(fingerVelocityTemp.getZ())));
                    
                if (fingerVelocity >= minVelocity) { 
                	return true; 
                } 
                else if (fingerVelocity <= maxVelocity) {
                	validPoseFrame = true;
                	break;
                }
            }
        }
        
        if (validPoseFrame) {
        	poseFrameCount++;
        	System.out.println("pose frame count: " + poseFrameCount);
        	gestureFrameCount = 0;
        	if (poseFrameCount >= minPoseFrames) {
        		validPose = true;
        		poseFrameCount = 0;
        		return true;
        	}
        } 
        else {
    		poseFrameCount = 0;
    	}      
        return false;
    }
   
    public void storePoint(Frame frame) {  	
    	for (Hand hand : frame.hands()) {
    		gesture.addPoint(new Point(hand.stabilizedPalmPosition()));
    		gesture.addPoint(new Point(hand.direction()));
    		gesture.addPoint(new Point(hand.palmNormal()));
    		for (Finger finger : hand.fingers()) {
    			gesture.addPoint(new Point(finger.stabilizedTipPosition()));
    			gesture.addPoint(new Point(finger.bone(Type.TYPE_METACARPAL).nextJoint().minus(hand.palmPosition())));
    			gesture.addPoint(new Point(finger.bone(Type.TYPE_PROXIMAL).nextJoint().minus(hand.palmPosition())));
    			gesture.addPoint(new Point(finger.bone(Type.TYPE_INTERMEDIATE).nextJoint().minus(hand.palmPosition())));
    			gesture.addPoint(new Point(finger.bone(Type.TYPE_DISTAL).nextJoint().minus(hand.palmPosition())));
    		}
    	}
    }
    
    public void saveGesture(Gesture gesture) {
    	try {
    		System.out.println("saving " + gesture.getName());
    		FileOutputStream outStream = new FileOutputStream(new File("gestures/" + gesture.getName()), false);
    		ObjectOutputStream objOutStream = new ObjectOutputStream (outStream);
    		objOutStream.writeObject(gesture);
    		objOutStream.close();
    		outStream.close();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}