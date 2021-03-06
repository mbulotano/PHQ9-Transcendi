package morningsignout.phq9transcendi.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import morningsignout.phq9transcendi.PHQApplication;
import morningsignout.phq9transcendi.R;
import morningsignout.phq9transcendi.HelperClasses.QuestionData;
import morningsignout.phq9transcendi.HelperClasses.Scores;
import morningsignout.phq9transcendi.HelperClasses.Utils;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class QuizActivity extends AppCompatActivity {
    private static final String LOG_NAME = "QuizActivity";
    private static final String SAVE_TIMESTAMP = "Timestamp", SAVE_QUESTION_NUM = "Question Number",
        SAVE_SCORES_A = "Score Values", SAVE_SCORES_B = "Visit values";
    private static final int DFLT_QUESTION_FONT_SIZE = 24; // in Pixels
    public static final int MAX_NUM_BUTTONS = 5;

    // Use String.format() with this to display current question
    private String numberString;

    private TextView questionTextView, questionNumText; //The text of the question
    private ImageButton prevArrow;
    private RadioGroup radioButtonGroup;

    QuestionData questionData;
    int currentAnswerChoice;
    private String startTimestamp, endTimestamp;
    private Scores scores;                          // Used for keeping track of score
    private int questionNumber;                     // Which question the user is on (zero-based)
    private boolean isFinishingFlag;                // Used in onPause() to save/not save

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int theme = Utils.GetTheme(this);
        Utils.onActivityCreateSetTheme(this, theme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // Grab and set content; inital setup (Use restoreInstanceState() for "continue where you last left off" code)
        questionTextView = findViewById(R.id.questionView);
        questionNumText = findViewById(R.id.textView_question_number);
        prevArrow = findViewById(R.id.imageButton_prevq);
        radioButtonGroup = findViewById(R.id.answer_choices);

        //change color according to theme
        Drawable arrows = ContextCompat.getDrawable(getApplicationContext(), R.drawable.green_arrow);
        ImageView whiteLine = findViewById(R.id.imageView_white_line);
        assert arrows != null;

        if(Utils.GetTheme(this)== 0){
            arrows.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.jungle_mist), PorterDuff.Mode.SRC_ATOP);
            whiteLine.setBackgroundColor(getResources().getColor(R.color.jungle_mist));
        }
        else{
            arrows.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.wafer), PorterDuff.Mode.SRC_ATOP);
            whiteLine.setBackgroundColor(getResources().getColor(R.color.wafer));
        }
        ((ImageButton)findViewById(R.id.imageButton_nextq)).setImageDrawable(arrows);
        ((ImageButton)findViewById(R.id.imageButton_prevq)).setImageDrawable(arrows);

        // Autofit setup for Question textView
        questionTextView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top > 0) {
                    Rect bounds = new Rect();
                    questionTextView.getLineBounds(1, bounds);
                    float lineHeight = DFLT_QUESTION_FONT_SIZE
                            * getResources().getDisplayMetrics().scaledDensity;
                    float spacingHeight = bounds.height() - lineHeight;
                    float maxTextHeight = questionTextView.getHeight()
                            - questionTextView.getPaddingTop()
                            - questionTextView.getPaddingBottom();
                    int maxLines = (int) (maxTextHeight / (lineHeight + spacingHeight));
                    questionTextView.setMaxLines(maxLines);
                    v.removeOnLayoutChangeListener(this);
                }
            }
        });

        isFinishingFlag = false;
        try {
            questionData = new QuestionData(this);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open resources");
        }
        currentAnswerChoice = -1;

        startTimestamp = getCurrentTimestampStr();
        questionNumber = -1;
        numberString = "%1$d/" + String.valueOf(questionData.questionsLength());
        scores = new Scores(questionData);

        changeCurrQuestion(true);   // Everything is set up, start quiz
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save answers and state variables
        if (!isFinishingFlag) {
            SharedPreferences.Editor editor = getPreferences(0).edit();
            editor.putString(SAVE_TIMESTAMP, startTimestamp);
            editor.putInt(SAVE_QUESTION_NUM, questionNumber);
            editor.putString(SAVE_SCORES_A, scores.getScoreString());
            editor.putString(SAVE_SCORES_B, scores.getVisitedString());
            editor.apply();
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Reinitialize state variables
        SharedPreferences preferences = getPreferences(0);
        if (savedInstanceState != null && preferences.contains(SAVE_TIMESTAMP)) {
            startTimestamp = preferences.getString(SAVE_TIMESTAMP, getCurrentTimestampStr());
            int questionNumber = preferences.getInt(SAVE_QUESTION_NUM, 0);
            String scoresA = preferences.getString(SAVE_SCORES_A, null);
            String scoresB = preferences.getString(SAVE_SCORES_B, null);
            scores = new Scores(questionData, scoresA, scoresB);

            setQuestion(questionNumber);    // Everything is set up, start quiz
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));   // For custom Rubik font
    }

    @Override
    public void onBackPressed() {
        confirmUserWishesToQuitDialog();
    }

    public void onClickNextArrow(View view) {
        // User clicked next arrow too quickly after the last question. Due to how Android works,
        // there's a brief period of time between the call to finish() in changeCurrQuestion() and the
        // switch to the next activity. If the user clicks next again, this code will still be
        // called and an OutOfBoundsException will occur.
        if (questionNumber == questionData.questionsLength())
            return;

        recordRadioButtonAnswer();
        changeCurrQuestion(true);
    }

    public void onClickPrevArrow(View view) {
        recordRadioButtonAnswer();
        changeCurrQuestion(false);
    }

    private void confirmUserWishesToQuitDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setMessage(R.string.dialog_quit_quiz)
                .setPositiveButton(R.string.dialog_return_home, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent backToMenu = new Intent(QuizActivity.this, IndexActivity.class);
                        startActivity(backToMenu);
                        finish();
                    }
                }).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        dialogBuilder.create().show();
    }

    // Calls all functions to change question.
    // bool isNextQuestion determines if questionNumber increases/decreases.
    private void changeCurrQuestion(boolean goToNextQuestion) {
        if (goToNextQuestion) {
            questionNumber++;

            if (questionNumber == questionData.questionsLength()) {
                if (scores.allQuestionsVisited()) {
                    finishQuiz();
                } else {
                    notifyUserQuizUnfinishedDialog();
                    questionNumber--;
                }
            }
            else {
                updateQuestionTextAndViews();
            }
        } else {
            questionNumber--;
            updateQuestionTextAndViews();
        }
    }

    private void notifyUserQuizUnfinishedDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setMessage(R.string.dialog_not_finished)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        dialogBuilder.create().show();
    }

    private void setQuestion(int questionNumber) {
        if (questionNumber >= 0 && questionNumber < questionData.questionsLength()) {
            this.questionNumber = questionNumber;
            updateQuestionTextAndViews();
        }
    }

    private void finishQuiz() {
        endTimestamp = getCurrentTimestampStr();
        uploadToDatabase();

        clearPreferences();
        isFinishingFlag = true;

        Intent results;
        results = new Intent(this, ResultsActivity.class);
        results.putExtra(ResultsActivity.SCORE, scores.getFinalScore());
        results.putExtra(ResultsActivity.RED_FLAG, scores.containsRedFlag());
        results.putExtra(ResultsActivity.RED_FLAG_BITS, scores.getRedFlagBits());
        results.putExtra(ResultsActivity.FAM_OR_CULTURE_BITS, scores.getFamilyOrCultureBits());
        results.putExtra(ResultsActivity.FAMILY_UNDERSTANDS_ANSWER, scores.getFamilyUnderstandsAnswer());
        results.putExtra(ResultsActivity.CAN_SEE_ANSWER, scores.getiAppointmentAnswer());
        startActivity(results);
        finish();
    }

    //changes the question text, answer text, and answer method
    //also checks the radiobutton that was previously chosen
    private void updateQuestionTextAndViews() {
        // Question text
        questionTextView.setText(questionData.getQuestionText(questionNumber));
        // Question # (+1 because users don't think in zero-based indices)
        questionNumText.setText(String.format(numberString, questionNumber + 1));
        // Answer text
        changeAnswerViews();

        // Hide previous button on first question
        if (questionNumber == 0)
            prevArrow.setVisibility(View.INVISIBLE);
        else if (prevArrow.getVisibility() != View.VISIBLE)
            prevArrow.setVisibility(View.VISIBLE);
    }

    private void setRadioButtonSelected(int lastSavedAnswer) {
        radioButtonGroup.clearCheck();
        RadioButton rb = ((RadioButton)radioButtonGroup.getChildAt(lastSavedAnswer));
        rb.setChecked(true);
    }

    //sets the radio buttons to be visible and buttons to be invisible
    //Number of buttons is determined by what question it is
    //question number is passed in
    private void changeRadioButtonsVisible(int numButtons) {
        int i;
        for (i= 0; i < numButtons; i++) {
            RadioButton rb = (RadioButton)radioButtonGroup.getChildAt(i);
            rb.setVisibility(View.VISIBLE);
        }
        for (; i < MAX_NUM_BUTTONS; i++) {
            RadioButton rb = (RadioButton)radioButtonGroup.getChildAt(i);
            rb.setVisibility(View.GONE);
        }
    }

    private void setRadioButtonText(String[] newText) {
        radioButtonGroup.clearCheck();
        //loop through and set each button
        for (int i = 0; i < newText.length; i++) {
            RadioButton rb = (RadioButton) radioButtonGroup.getChildAt(i);
            rb.setText(newText[i]);
        }
    }

    private void changeAnswerViews() {
        String answerType = questionData.getAnswerType(questionNumber);
        String[] newText = questionData.getAnswerValues(answerType);

        setRadioButtonText(newText);
        if (scores.questionIsVisited(questionNumber)) {
            setRadioButtonSelected(scores.getQuestionScore(questionNumber));
        }
        changeRadioButtonsVisible(newText.length);
    }

    private void addScore(int questionNumber, int value) {
        scores.putScore(questionNumber, value);
    }

    public void recordRadioButtonAnswer(){
        int currentAnswerChoice = -1;
        int numRadioButtons = radioButtonGroup.getChildCount();

        for (int i = 0; i < numRadioButtons; i++) {
            RadioButton rb = (RadioButton) radioButtonGroup.getChildAt(i);
            if (rb.isChecked()) {
                currentAnswerChoice = i;
            }
        }

        if (currentAnswerChoice != -1) {
            addScore(questionNumber, currentAnswerChoice);
        }
        // FIXME: For debugging ONLY!!!!!!!!!!!!!!!!!!!!
        else {
            addScore(questionNumber, 0);
        }
    }

    // FIXME: Use a class like DateFormat next time
    String getCurrentTimestampStr() {
        String timestamp = "";
        Calendar date = Calendar.getInstance();
        date.setTime(new Date());

        String month = String.valueOf(date.get(Calendar.MONTH) + 1);
        String day = String.valueOf(date.get(Calendar.DAY_OF_MONTH));
        String hour = String.valueOf(date.get(Calendar.HOUR_OF_DAY));
        String minute = String.valueOf(date.get(Calendar.MINUTE));

        if (date.get(Calendar.MONTH) + 1 < 10) month = "0" + month;
        if (date.get(Calendar.DAY_OF_MONTH) < 10) day = "0" + day;
        if (date.get(Calendar.HOUR_OF_DAY) < 10) hour = "0" + hour;
        if (date.get(Calendar.MINUTE) < 10) minute = "0" + minute;

        timestamp += String.valueOf(date.get(Calendar.YEAR)) + "-";
        timestamp += month + "-";
        timestamp += day + " at ";
        timestamp += hour + ":";
        timestamp += minute;

        return timestamp;
    }

    void clearPreferences() {
        SharedPreferences.Editor editor = getPreferences(0).edit();
        editor.remove(SAVE_TIMESTAMP);
        editor.remove(SAVE_QUESTION_NUM);
        editor.remove(SAVE_SCORES_A);
        editor.remove(SAVE_SCORES_B);
        editor.apply();
    }

    /* Uploads score data to Firebase. If no user ID exists, creates and stores one
     * TODO: Test this function. Just moved code out of Scores into here. */
    private void uploadToDatabase() {
        FirebaseUser user = FirebaseAuth.getInstance(PHQApplication.getFirebaseAppInstance()).getCurrentUser();

        if(user != null) {
            FirebaseDatabase rootDB = FirebaseDatabase.getInstance(PHQApplication.getFirebaseAppInstance());
            DatabaseReference userRef = rootDB.getReference("users/" + user.getUid()),
                    testRef = rootDB.getReference("tests/").push();
            String testID = testRef.getKey();

            // Users
            userRef.child("testIDs").push().setValue(testID);

            // Tests
            ArrayList<Integer> answers = scores.getScoreValsArray();
            Map<String, Integer> categoryScores = scores.getCategoryScoreMap();

            testRef.child("timestamp").setValue(endTimestamp);
            testRef.child("userID").setValue(user.getUid());
            testRef.child("answers").setValue(answers);
            testRef.child("scores").setValue(categoryScores);
        }
    }
}