<#include "master.ftl" />
<#import "pagination.ftl" as pagination />
<#import "ordinal.ftl" as ordinal />

<#macro content>
    <#if currentPage=1>
        <p>This site tracks <em>rating</em> for Stack Overflow users. As opposed to <em>reputation</em>, which is a cumulative measure of how much the community has appreciated a users contributions, the rating is a relative measure of a users &ldquo;skill&rdquo; compared to the other users. For further details on the algorithm, refer to the <a href="/about"><em>About</em></a> page.</p>
    </#if>

    <div style="width: 85%; margin: 2.5em auto 2em;">
        <div style="float: left; margin: 0em;">Page: <@pagination.pages 1..numPages currentPage /></div>
        <div style="float: right;">
            <form method="GET">
                <input type="hidden" name="page" value="${currentPage}" />
                User ID (<a href="http://meta.stackexchange.com/questions/98771/what-is-my-user-id">?</a>): <input type="text" name="userId" size="7" />
                <input type="submit" value="find" />
                <#if userNotFound>
                    <span style="color: red">User not found</span>
                </#if>
                <#if useNumericId>
                    <span style="color: red">Use <a style="color: red; text-decoration: underline" href="http://meta.stackexchange.com/questions/98771/what-is-my-user-id">numeric id</a></span>
                </#if>
            </form>
        </div>
        <div style="clear: both">&nbsp;</div>
        <table style="width: 100%;">
            <tr>
                <th style="width: 34%;">User</th>
                <th style="text-align: center; width: 1%; padding-right: 4em;" colspan="2">
                    Rating&nbsp;
                    <#if sortBy="rep">
                        <a href="/list/byRating"><img src="/down-black.png" /></a>
                    <#else>
                        <img src="/down-grey.png" />
                    </#if>
                </th>
                <th style="text-align: right; width: 1%;" colspan="2">
                    Reputation&nbsp;
                    <#if sortBy="rating">
                        <a href="/list/byRep"><img src="/down-black.png" />
                    <#else>
                        <img src="/down-grey.png" />
                    </#if>
                </th>
            </tr>
            <#list users as user>
                <#if user.id = highlightedUser>
                    <tr class="highlighted" id="scrollTo">
                <#else>
                    <tr>
                </#if>
                    <td><a href="/user/${user.id?c}">${user.displayName}</a></td>
                    <td style="text-align: right;">${user.rating?string["0.00"]}</td>
                    <td style="text-align: center; padding-right: 4em;" class="secondary">${user.ratingPos}<span style="font-size: 60%">${ordinal.getOrdinalSuffix(user.ratingPos)}</span></td>
                    <td style="text-align: right;">${user.rep}</td>
                    <td style="text-align: center;" class="secondary">${user.repPos}<span style="font-size: 60%">${ordinal.getOrdinalSuffix(user.repPos)}</span></td>
                </tr>
            </#list>
        </table>
    </div>
</#macro>

<@display_page />
