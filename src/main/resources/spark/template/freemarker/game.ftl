<#include "master.ftl" />
<#import "rating.ftl" as rating />

<#macro content>
    <div style="text-align: center; margin-top: 2.5em">Answers and rating deltas for</a></div>
    <h2 style="margin-top: 0em;"><a href="http://stackoverflow.com/questions/${game.id?c}">${game.title}</a></h2>
    
    <div style="width: 85%; margin: 0px auto 2em;">
        <table style="width: 100%;">
            <tr>
                <th>Author</th>
                <th style="text-align: center">Votes</th>
                <#-- <th style="text-align: center">Age (in days) when scraped</th> -->
                <th style="width: 1%; text-align: center">&#916;</th>
            </tr>
            <#list entries as entry>
                <tr>
                    <td><a href="/user/${entry.playerId?c}">${entry.userDisplayName}</a></td>
                    <td style="text-align: center">${entry.votes}</td>
                    <#-- <td style="text-align: center">${entry.getAgeInDays()?ceiling}</td> -->
                    <td style="text-align: right;"><@rating.ratingDelta val=entry.ratingDelta /></td>
                </tr>
            </#list>
        </table>
    </div>
    
    <div style="text-align: center; font-size: 60%;" class="secondary">Last visited: ${game.lastVisit}</div> 
</#macro>

<@display_page />
