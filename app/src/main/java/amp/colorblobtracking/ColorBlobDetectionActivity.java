package amp.colorblobtracking;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Point;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    private int                  maxVals=10000;
    static private ArrayList   <Double>         locations = new ArrayList();
    static private ArrayList        <Long>    timeStamps = new ArrayList();
    static private ArrayList      <Double>      instantVelocities = new ArrayList();
    static private double       mass = 1;
    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean              isTracking = false;
    private Button               startTracking;
    private Button               stopTracking;
    private SeekBar              seekBarH, seekBarS, seekBarV;
    private int                  Hval, Sval, Vval;
    private Scalar               colorRadius;
    private TextView             trackStatus;
    //private boolean              isStart = false;
    private int                  index = -1;
//    private double               distance = 0.0;
//    private double                  distanceTraveled = 0;
   private double               angle = 0.0;
    private boolean                 foundAngle = true;
    double distanceTraveled = 0;


    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        trackStatus = (TextView) findViewById(R.id.tvTrackingUI);
        startTracking = (Button)findViewById(R.id.trackButtonUI);
        stopTracking = (Button)findViewById(R.id.stopButtonUI);



        startTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = true;
                foundAngle = false;
                Log.e(TAG,"Tracking turned ON!");
                trackStatus.setText("Tracking ON");

            }
        });

        stopTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = false;
                Log.e(TAG,"Tracking turned OFF!");
                index = 0;
                if (locations.size() > 0){
                    double avgSpeed = getAverageVel();
                    trackStatus.setText("Tracking OFF! Avg. Speed = " + avgSpeed);
                }
                else{
                    trackStatus.setText("Tracking OFF!");
                }
                locations = new ArrayList();
                timeStamps = new ArrayList();
                instantVelocities = new ArrayList();

            }
        });

        seekBarH = (SeekBar)findViewById(R.id.seekBarH);
        seekBarS = (SeekBar)findViewById(R.id.seekBarS);
        seekBarV = (SeekBar)findViewById(R.id.seekBarV);


        seekBarH.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentProg;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProg = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Hval = currentProg;
                Toast.makeText(getApplicationContext(),"Final H Val " + Hval,Toast.LENGTH_SHORT).show();

            }
        });

        seekBarS.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentProg;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProg = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Sval = currentProg;
                Toast.makeText(getApplicationContext(),"Final S Val " + Sval,Toast.LENGTH_SHORT).show();

            }
        });

        seekBarV.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int currentProg;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProg = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Vval = currentProg;
                Toast.makeText(getApplicationContext(),"Final V Val " + Vval,Toast.LENGTH_SHORT).show();

            }
        });
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            Log.d(TAG, "Everything should be fine with using the camera.");
        } else {
            Log.d(TAG, "Requesting permission to use the camera.");
            String[] CAMERA_PERMISSIONS = {
                    Manifest.permission.CAMERA
            };
            ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, 0);
        }

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(100, 32);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;
        Toast.makeText(this,"RGB = " + mBlobColorRgba,Toast.LENGTH_LONG).show();
        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);


//        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;


        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        colorRadius = new Scalar(Hval,Sval,Vval,0);

        if (mIsColorSelected) {
            mDetector.setColorRadius(colorRadius);
            mDetector.process(mRgba);

            List<MatOfPoint> contours = mDetector.getContours();

            MatOfPoint cnt;
            //Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR,3);
            MatOfPoint2f approxCurve = new MatOfPoint2f();

            /*if(contours.size() == 1) {
                if (isStart)
                    isTracking = true;
                else
                    isTracking = false;
            }
            else {
                isTracking = false;
            }*/

            if(contours.size() == 1) {

                    //Convert contours(i) from MatOfPoint to MatOfPoint2f
                    MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(0).toArray());
                    //Processing on mMOP2f1 which is in type MatOfPoint2f
                    double approxDistance = Imgproc.arcLength(contour2f, true) * 0.02;
                    Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);

                    //Convert back to MatOfPoint
                    MatOfPoint points = new MatOfPoint(approxCurve.toArray());

                    // Get bounding rect of contour
                    Rect rect = Imgproc.boundingRect(points);

                    // draw enclosing rectangle (all same color, but you could use variable i to make them unique)
                    Point center = new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
                    Point pt1 = new Point(center.x - 2, center.y - 2);
                    Point pt2 = new Point(center.x + 2, center.y + 2);



                if (!foundAngle){
                    angle = findAngle(center);
                    foundAngle = true;
                    distanceTraveled = findDistanceTraveled(angle, findDistance());
                }


                    if (locations.size() != 0) {
//                        Imgproc.putText(mRgba, getAverageVel() + "", rect.tl(), Core.FONT_HERSHEY_SIMPLEX, 1.5, new Scalar(255, 0, 0), 5);
                        Imgproc.putText(mRgba, "" + angle, rect.tl(), Core.FONT_HERSHEY_SIMPLEX, 1.5, new Scalar(255, 0, 0), 5);
                    }
                    Imgproc.rectangle(mRgba, pt1, pt2, CONTOUR_COLOR, 5);

                   // System.out.println("X: " + center.x);
                   if (isTracking) {
                       if (maxVals  >  locations.size()) {
                           locations.add(center.x);
                           Log.e(TAG,"X: " + center.x);
                           timeStamps.add(System.nanoTime());
                           Log.e(TAG,"Time: " + System.nanoTime());
                           index++;
                       }
                       else
                       {
                           Log.e(TAG,"Max Capacity of List Reached !");
                       }
                   }
            }
           //  Mat colorLabel = mRgba.submat(4, mSpectrum.rows(), 4, mSpectrum.rows());
           // colorLabel.setTo(mBlobColorRgba);

           // Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
           // mSpectrum.copyTo(spectrumLabel);
        }

        return mRgba;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    public static double getPower(double k, double t){
        return k/t;
    }

    public double getChangeInTime(){
        return timeStamps.get(timeStamps.size() - 2) - timeStamps.get(1);
    }

    public double getChangeInKinetic(){
        double finalVelocity = instantVelocities.get(instantVelocities.size() - 1);
        double initialVelocity = instantVelocities.get(0);
        return .5 * mass * (Math.pow(finalVelocity, 2) - Math.pow(initialVelocity, 2));
    }

    public double getAverageVel(){
        int len = locations.size();
        if (index < 7){
            return (locations.get(len - 1 ) - locations.get(0))/(timeStamps.get(len - 1) - timeStamps.get(0)) * Math.pow(10, 9);
        }
        else{
            return (locations.get(len - 1 ) - locations.get(index - 5))/(timeStamps.get(len - 1) - timeStamps.get(index - 5)) * Math.pow(10, 9);
        }
    }

    public void makeInstantVelocities(){
        for (int i = 1; i < locations.size() - 1; i++){
            double x1 = locations.get(i - 1);
            double x2 = locations.get(i + 1);
            Long t1 = timeStamps.get(i - 1);
            Long t2 = timeStamps.get(i + 1);
            instantVelocities.add((x2 - x1)/(t2 - t1));
        }
    }

    public double findDistance(){
       //distance = focal length * height/sensor length
        return 30.45 * .22/4.53;
    }

    public double findAngle(Point center){
        double x = 1920/2;
        double y = 1080/2;
        return .0153994 * Math.sqrt(Math.pow(x - center.x, 2) + Math.pow(y - center.y, 2));
    }

    public double findDistanceTraveled(double angle, double distance){
        return 2 * distance * Math.tan(angle);
    }

}
