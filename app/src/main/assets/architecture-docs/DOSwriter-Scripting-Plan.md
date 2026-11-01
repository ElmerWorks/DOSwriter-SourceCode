1. Integration with CommandDispatcher
DOSwriter already uses a centralized Command sealed class and a CommandDispatcher. To support scripting, we would simply need a "Translator" class that maps string identifiers found in your Markdown (e.g., CTRL-B, SAVE_AS, TOGGLE_TYPEWRITER) into the existing Command objects.
Since the CommandDispatcher is already decoupled from the keyboard hardware, it won't know the difference between a command coming from a physical keypress and one coming from a script.
2. The Scripting Format (Markdown)
Using Markdown is a clever choice because it allows the "script" to be human-readable documentation and an executable file simultaneously. We could look for specific "Script Blocks":
TOML
# This is a comment
[DELAY] 500
[CMD] CTRL-B
[TYPE] This is automated text being typed...
[DELAY] 1000
[CMD] ALT-S
[STEP]
[CMD] CTRL-P
3. Execution & Timing Logic
•
Asynchronous Loop: Similar to the TypewriterDemo or Slideshow, we would use an android.os.Handler loop. This ensures that the UI remains responsive and the screen can refresh between commands—crucial for E-ink visibility.
•
The [DELAY] Parameter: This would simply update the postDelayed value of the handler for the next step.
•
The [STEP] Logic: When the engine hits a [STEP] tag (or if a global "Step Mode" is active), it would pause the Handler and wait for a specific "Step Key" listener (e.g., the Space bar or F8) to trigger the next command in the queue.
4. Compatibility with Existing Features
•
Split-Screen: Since the script executes commands through the dispatcher, it can handle ALT-N to swap focus and run different commands on different buffers.
•
Vietnamese TELEX: If the script uses the [TYPE] command, the text would still flow through the TelexProcessor (if enabled), allowing you to automate the testing of complex Vietnamese syllable clusters.
•
Animated GIFs: A script could be used to precisely time text entry alongside specific frames of an animation for a perfect demo.
5. Level of Effort (LOE)
•
Feasibility: Very High.
•
LOE: Medium. The primary work involves building the string-to-command parser and a small "Script Controller" UI (perhaps a small status overlay showing the "Current Instruction").
Verdict: This would be a powerful tool for regression testing and creating automated "Tutorial Demos" that show users how to use the app by actually driving the interface in real-time. It’s a natural extension of the "Workstation" philosophy.