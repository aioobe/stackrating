<#macro scripts>
</#macro>

<#macro content>
</#macro>

<#macro display_page>
    <!DOCTYPE html>
    <html>
        <head>
            <title>StackRating</title>
            <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
            <link rel="icon" href="/favicon.ico" type="image/x-icon">
            <style>
                body {
                    color: #333333;
                    font-family: Georgia, Serif;
                }

                #content {
                    max-width: 800px;
                    margin: 0px auto;
                    text-align: justify;
                }

                h1 {
                    text-align: center;
                    font-size: 250%;
                    font-style: italic;
                    margin-bottom: .1em;
                }

                h2 {
                    text-align: center;
                    margin-top: 1.5em;
                    margin-bottom: 0.5em;
                }

                h3 {
                    margin-bottom: 0em;
                }

                .subheader {
                    text-align: center;
                    color: #888888;
                    font-style: italic;
                    margin-bottom: 1.5em;
                }

                .secondary {
                    color: #AAAAAA;
                }

                a {
                    text-decoration: none;
                }

                table {
                    border-top: 2px solid black;
                    border-bottom: 2px solid black;
                    border-collapse: collapse;
                }

                th {
                    border-bottom: 1px solid black;
                    padding: .5em;
                    white-space: nowrap;
                }

                tr.highlighted {
                    border: 3px solid grey;
                }

                td {
                    padding: .5em;
                    vertical-align: bottom;
                }

                td > div {
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
            </style>

            <!-- GOOGLE ANALYTICS -->
            <script>
                (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
                (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
                m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
                })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
                ga('create', 'UA-62262792-1', 'auto');
                ga('send', 'pageview');
            </script>
            
            <@scripts /> 
        </head>
        <body>
            <div id="content" style="padding-bottom: 1em;">
                <h1>StackRating</h1>
                <div class="subheader">An Elo-based rating system for Stack Overflow</div>
                <div style="text-align: center"><#if (show!"") = "home">Home<#else><a href="/">Home</a></#if> &nbsp;&nbsp;|&nbsp;&nbsp; <#if (show!"") = "about">About<#else><a href="/about">About</a></#if> &nbsp;&nbsp;|&nbsp;&nbsp; <#if (show!"") = "stats">Stats and Analysis<#else><a href="/stats">Stats and Analysis</a></#if> &nbsp;&nbsp;|&nbsp;&nbsp; <#if (show!"") = "badge">Get a Badge<#else><a href="/getBadge">Get a Badge</a></#if></div>
                <@content />
            </div>
            <script type="text/javascript">
                var elem = document.getElementById("scrollTo");
                if (typeof elem !== 'undefined' && elem !== null) {
                    elem.scrollIntoView();
                }
            </script>
        </body>
    </html>
</#macro>
