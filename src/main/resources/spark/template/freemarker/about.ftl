<#include "master.ftl" />

<#macro content>

    <h2 style="text-align: left; margin-top: 1.5em;">Reputation vs Rating</h2>
    <p>
        Reputation is a measure of how much the community has appreciated a users contributions (which strongly correlates to how much time the user has been spent on the site). The reputation systems primary purpose is to asses a users trustworthiness and automatically ascribe moderator capabilities. For details, refer to <a href="http://meta.stackexchange.com/questions/12421/what-does-reputation-really-mean-and-do-you-pay-attention-to-anyones-but-your-o"><em>What does reputation really mean and do you pay attention to anyone&rsquo;s but your own?</em></a>.
    </p>
    <p>
        StackRating on the other hand measures a users &ldquo;skill&rdquo;, which in this context corresponds to a users ability to provide answers which the community appreciates.
    </p>

    <h2 style="text-align: left" id="how">How the rating is computed</h2>
    <p>
        Each user has an initial rating of 1500. When a user answers a question, his/her rating will get a positive update if the answer gets more upvotes than the other answers on the same question, and a negative update if it gets less upvotes. The magnitude of the rating update is determined by the ratings of the other users and whether or not the outcome was expected.
    </p>
    <p>
        <strong>Example:</strong> You and Jon Skeet each post an answer to the same question. Jon&rsquo;s answer gets 20 upvotes, yours get 5. Since Jon beat you, he gets a small positive update, and you get a small negative update. The updates are small, because the result was expected according to your prior ratings. If, on the other hand, you beat Jon, you will get a large positive update, and Jon a large negative update.
    </p>
    <p>
        The algorithm is called the <a href="http://en.wikipedia.org/wiki/Elo_rating_system"><em>Elo rating system</em></a> after Arpad Elo who invented it to track ratings of chess players.
    </p>
    <h3>Generalization to <i>N</i> players</h3>
    <p style="margin-top: .3em;">
        The original formula is only defined for two players. Since an arbitrary number of users can answer the same question, the algorithm has been generalized as follows: If four users answer the same question, the algorithm treats this as six separate games where each player is compared to each other player. This gives three rating updates for each player, and the final rating update is the average of these three updates.
    </p>

    <h3>Scoring</h3>
    <p style="margin-top: .3em;">
        Elos rating system gives 1, &frac12; and 0 points for a win, draw resp. loss. When applying the formulas to answers on Stack Overflow, we can leverage the actual votes and get a finer granularity. The function that StackRating uses, gives you <ul><li>1 if you have more upvotes than the other user</li><li>&frac12; if you have the same number of upvotes</li><li>0 if you have less then half of your opponents votes, and</li><li>interpolates linearly between loss and draw</li></ul>
    </p>
    <p>
        This ensures that the user with the top answer always gets a positive rating update, and a close runner up dosen&rsquo;t get a large negative update.
    </p>
    <p>
        A downvote counts as -1 upvote and an accept counts as +1 upvote.
    </p>

    <h3>Convergence</h3>
    <p style="margin-top: .3em;">
        The key property of the Elo rating system is that the ratings <em>converge</em>. This means that once you&rsquo;ve answered enough questions, your rating will reflect your actual ability, and you can expect it to be somewhat stable. The graph below illustrates this nicely:

        <center><img src="/714501-graph.png" /><br />The rating of <a href="/user/714501">cnicutar</a></center>
    </p>
    <p>
        How fast the rating converges depends on the <i>K</i>-value, which represent the maximum update for a game. A high value gives volatile ratings that converge quickly and a low value gives stable ratings that converge slowly. It&rsquo;s common practice to let this value depend on how many games you&rsquo;ve played. StackRating uses the following function:

    <div style="margin: 0 auto; display: table">
        <div style="display: table-row">
            <div style="display: table-cell"></div>
            <div style="display: table-cell; border-left: 1px solid black;">&nbsp;8</div>
            <div style="display: table-cell; padding-left: 1em;">if you have posted less than 100 answers</div>
        </div>
        <div style="display: table-row">
            <div style="display: table-cell; padding: .5em 0em;"><i>K</i>&nbsp;=&nbsp;</div>
            <div style="display: table-cell; border-left: 1px solid black;">&nbsp;1</div>
            <div style="display: table-cell; padding-left: 1em;">if your opponent has posted less than 100 answers</div>
        </div>
        <div style="display: table-row">
            <div style="display: table-cell"></div>
            <div style="display: table-cell; border-left: 1px solid black;">&nbsp;4</div>
            <div style="display: table-cell; padding-left: 1em;">otherwise</div>
        </div>
    </div>

    <h3>Further adjustments</h3>
    <p style="margin-top: .3em;">
        To mitigate the <a href="http://meta.stackexchange.com/questions/9731/fastest-gun-in-the-west-problem">Fastest Gun in the West Problem</a> the upvotes are normalized by the age of the answer.
    </p>
    <p>
        To avoid problems that arise when new answers are posted to old questions (&ldquo;Here&rsquo;s the new Java 8 way of doing this&hellip;&rdquo;) which might get an unfair advantage, only answers that are posted within 3 months of the time the question was posted are taken into consideration.
    </p>


    <h2 style="text-align: left" id="interpret">How to interpret the rating</h2>
    <p>
        A user&rsquo;s rating is increased if the he/she provides an answer to a question which gets more upvotes than other answers to the same question. In other words the rating reflects the users ability to provide answers that &ldquo;end up on top&rdquo;. Now the obvious follow up question is of course: <em>How well does a users capability to provide highly upvoted answers reflect his/her actual proficiency?</em>
    </p>

    <p>
        My personal experience using Stack Overflow says that the best answers typically end up on top. (While it is not hard to find questions where the objectively best answer is on second or third place, these belong to the exceptions, and the Elo rating system is stable enough to not wreck havoc in these cases). This reduces the question to: <em>Does the ability to answer programming questions reflect programming proficiency?</em> Personally I&rsquo;d say <em>yes, absolutely</em>. The capability of being able to describe a technical topic correlates to how well you understand the topic, and even if you&rsquo;re really good with inventing algorithms and fine tuning assembly code, what good is this ability if you can&rsquo;t explain your work to a fellow programmer. Communication (which Stack Overflow happens to be all about) is key!
    </p>

    <p>
        Finally, I&rsquo;ve looked through numerous users with high and low rating and my (completely anecdotal) observation is that the rating reflects quality and proficiency really well.
    </p>

    <h2 style="text-align: left">Live Monitoring</h2>
    <p>
        The Stack Overflow site is monitored continuously through the StackExchange API (thanks <a href="http://stackapps.com/users/22733/sanjiv">Sanjiv</a> for the Java API!). New questions are currently scanned every second minute. As the question gets older the scan frequency drops. After 3 months the question is no longer scanned.
    </p>

    <h2 style="text-align: left">Links to relevant StackExchange posts</h2>
    <ul>
        <li><a href="http://stackapps.com/questions/6298/stackrating-tracks-skill-of-stack-overflow-users">StackRating – Tracks skill of Stack Overflow users!</a>, StackApps</li>
        <li><a href="http://meta.stackexchange.com/questions/255830/has-anyone-tried-to-estimate-stack-overflow-users-skill-by-analyzing-the-data-du/">Has anyone tried to estimate Stack Overflow users skill by analyzing the data dumps?</a>, Meta</li>
    </ul>

    <h2 style="text-align: left">About the webpage</h2>
    <p>
        Original idea and code by me, <a href="http://stackoverflow.com/users/276052/aioobe">aioobe</a>. Lots of valuable feedback from <a href="http://stackoverflow.com/users/271357/dacwe">dacwe</a>.
    </p>
    <p>
        I did this as a &ldquo;weekend project&rdquo; and it&rsquo;s a bit of a one-off hack. I don&rsquo;t have any plans to continue the development. (I simply don&rsquo;t have time.) Please drop me an email if you&rsquo;re interested in taking this further.
    </p>
    <p>
        Finally, if you want to do a Greasemonkey script that embeds rating on Stack Overflow, or if you want to create a badge or something, you can use <code>stackrating.com/rating/&lt;userid&gt;</code>.
    </p>
</#macro>

<@display_page />
