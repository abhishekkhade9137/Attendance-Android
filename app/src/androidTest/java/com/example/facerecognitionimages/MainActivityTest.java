package com.example.facerecognitionimages;

import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void testBottomNavigationSwitchesToMembers() {
        // Click on the Members tab in the bottom navigation
        onView(withId(R.id.nav_members)).perform(click());
        
        // Verify the Add Member FAB is displayed
        onView(withId(R.id.btnAddMember)).check(matches(isDisplayed()));
    }

    @Test
    public void testAddMemberButtonLaunchesRegisterActivity() {
        // Navigate to Members fragment
        onView(withId(R.id.nav_members)).perform(click());
        
        // Click the Add Member button
        onView(withId(R.id.btnAddMember)).perform(click());
        
        // Verify that an intent to RegisterActivity was sent
        intended(hasComponent(RegisterActivity.class.getName()));
    }
}
