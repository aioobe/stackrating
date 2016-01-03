<#include "master.ftl" />

<#macro content>

    <h2>Your StackRating Badge</h2>
    <div style="text-align: center">
        To put a StackRating badge on for instance your Stack Overflow profile, use the image tag below (but replace <code>12345678</code> in BOTH places with your <a href="http://meta.stackexchange.com/questions/98771/what-is-my-user-id">user id</a>)<br /><br />

        <div style="text-align: center"><code>&lt;a href="http://stackrating.com/user/12345678"&gt;&lt;img src="http://stackrating.com/badge/12345678" /&gt;&lt;/a&gt;</code></div><br />

        Here&rsquo;s an example:<br /><br />

        <a href="http://stackrating.com/user/276052"><img src="/badge/276052" /></a>
    </div>
</#macro>

<@display_page />
