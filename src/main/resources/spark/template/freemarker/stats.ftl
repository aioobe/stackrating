<#include "master.ftl" />

<#macro content>

    <h2 style="text-align: left; margin-top: 1.5em;">Correlation between Reputation and &ldquo;Skill&rdquo;</h2>
    <p>
        Ever since I joined Stack Overflow I&rsquo;ve been curious about whether or not there is any correlation at all between skill and reputation. For instance: Is the distribution of skill roughly the same for the top 500 users as for 500 random users of around 10k rep?
    </p>
    <p>
        I thought long about how to estimate a users skill in a meaningful way. Accept rate and average score per answer came to mind, but neither felt very reliable. It suddenly struck me that the Elo rating system was pretty much a perfect fit. The rating system can be applied to questions ("games") whose outcome is determined by votes, and the ratings converge to values that reflect actual ability to provide good answers.
    </p>
    <p>
        I started by analyzing the most recent data dump and proceeded to download the missing data through the StackExchange REST API. When the rating of each user was computed (refer to <a href="/about#how"><em>How the rating is computed</em></a> and <a href="/about#interpret"><em>How to interpret the rating</em></a> for details) I created a rating/reputation plot. I present to you the result of my last days of hacking and number crunching:
    </p>
    <div style="text-align: center; margin-top: 2em;">
        <img src="/rep-vs-rating.png" /><br />
        <i><small>Heat map over rating vs reputation.</small></i>
    </div>
    <div style="text-align: center; margin-bottom: 2em;">
        <img src="/rating-dist.png" /><br />
        <i><small>Distribution of ratings.</small></i>
    </div>
    <p>
        It&rsquo;s quite easy to read and understand the plot, but I find it hand to draw any interesting conclusions. At first sight you can see that high rep users are typically also skilled users. While this is interesting in it self, one has to keep in mind that it takes a while to work up a good rating (due to the stability of the system) and to figure out Stack Overflow specific-tactics, so users with low rating can very well be just as skilled programmers.
    </p>
</#macro>

<@display_page />
