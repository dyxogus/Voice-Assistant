package dyxogus.com.assistant;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

/**
 * Recognises what the user is saying and takes appropriate actions. This utilises CMU Sphinx, which
 * allows continuous service of what the user is saying.
 * <p/>
 * It is most effective when used with English-US (as intended by design) and when the key-words are
 * in fact, phrases, thanks to it reverse-ngram traversal trie structure.
 * <p/>
 * Adapted code from https://github.com/cmusphinx/pocketsphinx-android-demo
 */
public class MainActivity extends Activity implements RecognitionListener {
    private static final String TAG = MainActivity.class.toString();
    private static final String COMMANDS = "commands";

    // Speech Recongnizer Delegate
    private SpeechRecognizer recognizer;

    // TextViews helps interaction with user
    private TextView outputView;
    private TextView gestureView;

    // List of recognisable commands
    private List<String> commands; // Make this into a Trie when it grows large
    private int[] colours;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);

        outputView = (TextView) findViewById(R.id.recognised_input_view);
        gestureView = (TextView) findViewById(R.id.gesture_view);
        gestureView.setText("Initialising...");

        // Asynchronously load assets and setup recogniser
        new InitialiseTask().execute();
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        parseHypothesis(hypothesis);
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        parseHypothesis(hypothesis);
    }

    private void parseHypothesis(Hypothesis hypothesis) {
        if (hypothesis == null) return;

        String prediction = hypothesis.getHypstr();

        for (int index = 0; index < commands.size(); index++) {
            String command = commands.get(index);

            if (prediction.equals(command)) {
                gestureView.setTextColor(Color.BLACK);
                gestureView.setText("You said");

                outputView.setTextColor(colours[index % colours.length]);
                outputView.setText(prediction);
                Log.i(TAG, prediction + " detected");
                Toast.makeText(getApplicationContext(), prediction, Toast.LENGTH_SHORT).show();
                break;
            }
        }

        startListeningForCommands();
    }

    @Override
    public void onEndOfSpeech() {
        startListeningForCommands();
    }

    private void startListeningForCommands() {
        recognizer.stop();
        recognizer.startListening(COMMANDS);
    }

    @Override
    public void onError(Exception error) {
        outputView.setText(error.getMessage());
        error.printStackTrace();
    }

    @Override
    public void onTimeout() {
        startListeningForCommands();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
    }

    private class InitialiseTask extends AsyncTask<Void, Void, Exception> {
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                // Synchronise the assets and setup recogniser
                Assets assets = new Assets(MainActivity.this);
                File assetsDirectory = assets.syncAssets();
                recognizer = defaultSetup()
                        .setAcousticModel(new File(assetsDirectory, "en-us-ptm"))
                        .setDictionary(new File(assetsDirectory, "cmudict-en-us.dict"))
                        .setRawLogDir(assetsDirectory)
                        .setKeywordThreshold(1e-5f) // Threshold that determines FP/TN rates
                        .setBoolean("-allphone_ci", true)
                        .getRecognizer();
                recognizer.addListener(MainActivity.this);

                // Read the file that contains the list of commands it sets to recognise
                File commandsFile = new File(assetsDirectory, "commands.lst");
                recognizer.addKeywordSearch(COMMANDS, commandsFile);
                commands = new ArrayList<String>();

                // Read the commands.lst using a FileInputStream Reader (character by character)
                try {
                    FileInputStream reader = new FileInputStream(commandsFile);
                    StringBuilder word = new StringBuilder(); // Buffer for efficiency

                    while (reader.available() > 0) {
                        char character = (char) reader.read();

                        if (character == '\n') {
                            // When the character is '\n', it's the end of command
                            // Trim to reduce errors in comparison stage
                            commands.add(word.toString().trim());
                            word = new StringBuilder();
                        } else {
                            // Append the character we read to the string buffer
                            word.append(character);
                        }
                    }

                    // Push the last word read in
                    if (word.length() != 0) commands.add(word.toString().trim());
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // populate colors to accomodate for different key words
                if (commands.size() > 5) Log.e(TAG, "Make more colors!");
                colours = new int[]{
                        Color.BLUE,
                        Color.GREEN,
                        Color.CYAN,
                        Color.MAGENTA,
                        Color.BLACK
                };
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                gestureView.setTextColor(Color.RED);
                gestureView.setText("FAILED TO INITIALISE");
                outputView.setTextColor(Color.RED);
                outputView.setText("RECOGNISER");
                result.printStackTrace();
            } else {
                gestureView.setTextColor(Color.RED);
                gestureView.setText("PLEASE");
                outputView.setTextColor(Color.RED);
                outputView.setText("SPEAK...");
                startListeningForCommands();
            }
        }
    }
}
